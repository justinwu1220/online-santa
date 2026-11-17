import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { ApiError, api } from './api'
import { AuthContext, devAuth, registerUnauthorizedHandler } from './authContext'
import type { AuthState } from './authContext'
import { queryClient } from './queryClient'

/**
 * 身分驗證。
 *
 * 有兩種模式，由 `VITE_DEV_AUTH` 決定——與後端的 `dev-auth` profile 對稱：
 *
 * - **帳號模式**（預設）：信箱＋密碼，註冊後用寄到信箱的 6 位數驗證碼確認信箱。
 *   登入狀態分兩段：短效的 access token 只放在記憶體（請求帶 `Authorization: Bearer`），
 *   長效的 refresh token 只放在後端設的 HttpOnly cookie——JavaScript 讀不到，XSS 偷不走。
 *   頁面載入時用 cookie 換一張 access token 還原登入狀態。
 * - **開發模式**：直接輸入 email 即可切換身分，請求帶 `X-Dev-User-Email`。
 *   它的存在讓整個專案不需要寄信就能跑起來。只在後端也啟用 `dev-auth` 時有效——
 *   正式環境的後端會直接回 401。
 */

const DEV_IDENTITY_KEY = 'online-santa.dev-identity'
const DEV_UNVERIFIED_KEY = 'online-santa.dev-unverified'

// 跟 api.ts 的 BASE_URL 是同一個環境變數。正式環境留空：前端與 API 同網域
// （Firebase Hosting 把 /api/** 轉給 Cloud Run），登入狀態 cookie 才不會被當成第三方 cookie 擋掉
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

/** 後端登入、註冊、換發回傳的內容。refresh token 不在裡面，它只在 cookie 裡。 */
interface SessionView {
  accessToken: string
  /** access token 距離過期的秒數 */
  expiresIn: number
  email: string
  emailVerified: boolean
}

/**
 * 呼叫不需要（或還沒有）access token 的帳號端點：註冊、登入、換發、登出、忘記密碼。
 *
 * 刻意不走 api.ts：api.ts 遇到 401 會請 AuthProvider 換發再重試，換發本身若也走那條路就會遞迴；
 * 而且這些請求<strong>不能帶</strong> Authorization 標頭。必須帶 `credentials: 'include'`——
 * 本機開發時前端與 API 不同來源，不帶的話瀏覽器不會存、也不會送登入狀態 cookie。
 */
async function authFetch<T>(path: string, body?: unknown): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) {
    // 後端統一以 RFC 7807 ProblemDetail 回覆錯誤，帶 errorCode。
    // 但在進到我們的程式之前就被擋下的請求（例如 Spring 的 CORS 檢查）回的是純文字，不是 JSON；
    // HTTP/2 的 statusText 又是空字串，只讀 JSON 的話這類錯誤會變成一則空白訊息
    const text = await response.text()
    let problem: { detail?: string } | undefined
    try {
      problem = text ? JSON.parse(text) : undefined
    } catch {
      problem = undefined
    }
    throw new ApiError(response.status, problem?.detail ?? (text || response.statusText), problem)
  }
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export function AuthProvider({ children }: { children: ReactNode }) {
  return devAuth
    ? <DevAuthProvider>{children}</DevAuthProvider>
    : <LocalAuthProvider>{children}</LocalAuthProvider>
}

// ---------------------------------------------------------------- 開發模式

function readStored(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    // 無痕視窗或封鎖了網站資料時讀不到，當作沒有即可
    return null
  }
}

function writeStored(key: string, value: string | null) {
  try {
    if (value === null) localStorage.removeItem(key)
    else localStorage.setItem(key, value)
  } catch {
    // 記不住就算了，重新整理後再輸入一次
  }
}

function DevAuthProvider({ children }: { children: ReactNode }) {
  const [email, setEmail] = useState<string | null>(() => readStored(DEV_IDENTITY_KEY))
  // 開發模式預設視為已驗證。登入頁提供開關可以模擬未驗證，用來在本機檢查
  // 驗證橫幅與被擋下的操作
  const [unverified, setUnverified] = useState(
    () => readStored(DEV_UNVERIFIED_KEY) === 'true')

  const signIn = useCallback(async (nextEmail?: string) => {
    const trimmed = nextEmail?.trim()
    if (!trimmed) return
    setEmail(trimmed)
    writeStored(DEV_IDENTITY_KEY, trimmed)
  }, [])

  const signOut = useCallback(async () => {
    setEmail(null)
    setUnverified(false)
    writeStored(DEV_IDENTITY_KEY, null)
    writeStored(DEV_UNVERIFIED_KEY, null)
  }, [])

  const setDevUnverified = useCallback((next: boolean) => {
    setUnverified(next)
    writeStored(DEV_UNVERIFIED_KEY, next ? 'true' : null)
  }, [])

  const value = useMemo<AuthState>(() => ({
    email,
    emailVerified: !unverified,
    loading: false,
    signIn,
    // 開發模式沒有真的密碼，登入頁也不會顯示密碼欄位；這些退化成一般的身分切換
    signInWithPassword: async (nextEmail) => { await signIn(nextEmail) },
    // 開發模式的註冊：密碼完全不使用（只讓註冊畫面和正式環境一樣），但姓名要存。
    // 直接帶 X-Dev-User-Email 打 API，不靠 context 的 authHeaders——
    // 剛 signIn 時 state 還沒更新，請求可能還在用登入前的標頭
    registerWithPassword: async (nextEmail, _password, displayName, name, consent) => {
      await signIn(nextEmail)
      const devHeaders = { 'Content-Type': 'application/json', 'X-Dev-User-Email': nextEmail.trim() }
      if (consent) {
        try {
          await fetch(`${API_BASE_URL}/api/me/terms/${consent.type}/accept`, {
            method: 'POST', headers: devHeaders, body: JSON.stringify({ version: consent.version }),
          })
        } catch (error) {
          console.warn('開發模式註冊後寫入須知同意紀錄失敗，之後會再請使用者同意', error)
        }
      }
      if (!displayName) return
      try {
        await fetch(`${API_BASE_URL}/api/me/profile`, {
          method: 'PATCH', headers: devHeaders, body: JSON.stringify({ displayName, name }),
        })
      } catch (error) {
        console.warn('開發模式註冊後寫入姓名失敗，不影響註冊本身', error)
      }
    },
    sendPasswordReset: async () => {},
    resendVerification: async () => setDevUnverified(false),
    // 開發模式沒有寄信，輸入任何驗證碼都視為通過
    verifyEmail: async () => setDevUnverified(false),
    signOut,
    authHeaders: async (): Promise<Record<string, string>> => {
      if (!email) return {}
      return unverified
        ? { 'X-Dev-User-Email': email, 'X-Dev-Email-Verified': 'false' }
        : { 'X-Dev-User-Email': email }
    },
    devUnverified: unverified,
    setDevUnverified,
  }), [email, unverified, signIn, signOut, setDevUnverified])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// ---------------------------------------------------------------- 帳號模式

/** access token 剩不到這麼多毫秒就先換發，避免請求送到後端時剛好過期。 */
const EXPIRY_MARGIN_MS = 30_000

interface LocalSession {
  email: string
  emailVerified: boolean
}

function LocalAuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<LocalSession | null>(null)
  const [loading, setLoading] = useState(true)

  // access token 只放記憶體（ref），不放 localStorage：XSS 讀得到 localStorage，讀不到這裡的閉包
  const token = useRef<{ value: string; expiresAt: number } | null>(null)
  const refreshTimer = useRef<number | undefined>(undefined)
  // 同一時間只換發一次：換發會輪替 cookie，兩個並行的換發會讓第二個用到已撤銷的舊 cookie
  const inflight = useRef<Promise<boolean> | null>(null)
  // applySession 與 refreshSession 互相參照（套用結果時要排下一次換發），用 ref 打破循環
  const refreshRef = useRef<() => Promise<boolean>>(async () => false)

  const clearSession = useCallback(() => {
    token.current = null
    window.clearTimeout(refreshTimer.current)
    setSession(null)
  }, [])

  const applySession = useCallback((view: SessionView) => {
    token.current = { value: view.accessToken, expiresAt: Date.now() + view.expiresIn * 1000 }
    setSession({ email: view.email, emailVerified: view.emailVerified })
    // 到期前先換發，使用者閒置後回來也不會遇到第一個請求才發現過期
    window.clearTimeout(refreshTimer.current)
    refreshTimer.current = window.setTimeout(
      () => void refreshRef.current(), Math.max(5_000, view.expiresIn * 1000 - 60_000))
  }, [])

  const refreshSession = useCallback((): Promise<boolean> => {
    if (inflight.current) return inflight.current
    const attempt = (async () => {
      try {
        applySession(await authFetch<SessionView>('/api/auth/refresh'))
        return true
      } catch (error) {
        // 4xx：cookie 沒有或已失效，真的登出了。網路錯誤則保留現況，下次請求再試
        if (error instanceof ApiError) clearSession()
        return false
      } finally {
        inflight.current = null
      }
    })()
    inflight.current = attempt
    return attempt
  }, [applySession, clearSession])

  useEffect(() => {
    refreshRef.current = refreshSession
    // api.ts 收到 401（access token 剛好過期）時請這裡換發一次
    registerUnauthorizedHandler(refreshSession)
  }, [refreshSession])

  // 頁面載入：用 cookie 還原登入狀態
  useEffect(() => {
    let cancelled = false
    void refreshSession().finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true; window.clearTimeout(refreshTimer.current) }
  }, [refreshSession])

  const signInWithPassword = useCallback(async (email: string, password: string) => {
    applySession(await authFetch<SessionView>('/api/auth/login', { email, password }))
  }, [applySession])

  const registerWithPassword = useCallback(
    async (email: string, password: string, displayName?: string, name?: string,
      consent?: { type: 'DONOR' | 'ORG'; version: string }) => {
      // 帳號、名稱、須知同意與驗證碼信在後端同一個交易裡完成，不再需要註冊後補打好幾支 API
      applySession(await authFetch<SessionView>('/api/auth/register', {
        email, password, displayName: displayName || undefined, name: name || undefined, consent,
      }))
    }, [applySession])

  const sendPasswordReset = useCallback(async (email: string) => {
    await authFetch<void>('/api/auth/password/forgot', { email })
  }, [])

  const resendVerification = useCallback(async () => {
    await api.post<void>('/api/auth/verification/resend')
  }, [])

  const verifyEmail = useCallback(async (code: string) => {
    await api.post<void>('/api/auth/verify-email', { code })
    setSession((current) => current && { ...current, emailVerified: true })
    // 身分與個人檔案的快取還記著「未驗證」，不重抓的話畫面會停在舊狀態
    await queryClient.invalidateQueries()
  }, [])

  const signOut = useCallback(async () => {
    try {
      await authFetch<void>('/api/auth/logout')
    } catch {
      // 連不上後端也要讓這個瀏覽器登出：丟掉記憶體裡的 token，cookie 之後自然過期
    }
    clearSession()
  }, [clearSession])

  const authHeaders = useCallback(async (): Promise<Record<string, string>> => {
    if (!token.current) return {}
    if (token.current.expiresAt - Date.now() < EXPIRY_MARGIN_MS) {
      await refreshSession()
    }
    return token.current ? { Authorization: `Bearer ${token.current.value}` } : {}
  }, [refreshSession])

  const value = useMemo<AuthState>(() => ({
    email: session?.email ?? null,
    emailVerified: session?.emailVerified ?? false,
    loading,
    // 開發模式專用；帳號模式沒有「只輸入 email 就登入」這回事
    signIn: async () => {},
    signInWithPassword,
    registerWithPassword,
    sendPasswordReset,
    resendVerification,
    verifyEmail,
    signOut,
    authHeaders,
  }), [session, loading, signInWithPassword, registerWithPassword, sendPasswordReset,
    resendVerification, verifyEmail, signOut, authHeaders])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
