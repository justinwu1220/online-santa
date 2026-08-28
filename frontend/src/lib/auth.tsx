import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { AuthContext, FIREBASE_CONFIG, usingFirebase } from './authContext'
import type { AuthState } from './authContext'

/**
 * 身分驗證。
 *
 * 有兩種模式，依 Firebase 設定是否齊全自動選擇——與後端的 `dev-auth` profile 對稱：
 *
 * - **Firebase 模式**：設定了 `VITE_FIREBASE_*` 時啟用，走真正的登入流程
 *   （Google 或 email/密碼），請求帶 `Authorization: Bearer <ID token>`
 * - **開發模式**：沒有 Firebase 設定時啟用，直接輸入 email 即可切換身分，
 *   請求帶 `X-Dev-User-Email`
 *
 * 開發模式的存在讓整個專案不需要任何雲端資源就能跑起來。它只在後端也啟用
 * `dev-auth` 時有效——正式環境的後端會直接回 401。
 */

const DEV_IDENTITY_KEY = 'online-santa.dev-identity'
const DEV_UNVERIFIED_KEY = 'online-santa.dev-unverified'

export function AuthProvider({ children }: { children: ReactNode }) {
  return usingFirebase
    ? <FirebaseAuthProvider>{children}</FirebaseAuthProvider>
    : <DevAuthProvider>{children}</DevAuthProvider>
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
  // 驗證橫幅與被擋下的操作——那正是這次改動的重點，不該只能在有 Firebase 專案時才測得到
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
    registerWithPassword: async (nextEmail) => { await signIn(nextEmail) },
    sendPasswordReset: async () => {},
    resendVerification: async () => setDevUnverified(false),
    refreshVerification: async () => {},
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

// ---------------------------------------------------------------- Firebase 模式

type FirebaseUser = {
  email: string | null
  emailVerified: boolean
  getIdToken: (force?: boolean) => Promise<string>
  reload: () => Promise<void>
}

function FirebaseAuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<FirebaseUser | null>(null)
  const [loading, setLoading] = useState(true)
  // 驗證狀態變更後用來重建 context 的值——emailVerified 不是 state，
  // 是從 user 物件讀的，而 reload() 是就地修改那個物件
  const [verificationTick, setVerificationTick] = useState(0)
  const [auth, setAuth] = useState<Awaited<ReturnType<typeof loadFirebaseAuth>> | null>(null)

  useEffect(() => {
    let cancelled = false
    loadFirebaseAuth().then((loaded) => {
      if (cancelled) return
      setAuth(loaded)
      loaded.onAuthStateChanged(loaded.auth, (next) => {
        setUser(next as FirebaseUser | null)
        setLoading(false)
      })
    })
    return () => { cancelled = true }
  }, [])

  const signIn = useCallback(async () => {
    if (!auth) return
    await auth.signInWithPopup(auth.auth, new auth.GoogleAuthProvider())
  }, [auth])

  const signInWithPassword = useCallback(async (email: string, password: string) => {
    if (!auth) return
    await auth.signInWithEmailAndPassword(auth.auth, email, password)
  }, [auth])

  const registerWithPassword = useCallback(
    async (email: string, password: string, displayName?: string) => {
      if (!auth) return
      const credential = await auth.createUserWithEmailAndPassword(auth.auth, email, password)
      if (displayName) {
        await auth.updateProfile(credential.user, { displayName })
      }
      // 註冊完立刻寄驗證信。在點連結之前，後端會擋下認領與機構申請
      await auth.sendEmailVerification(credential.user)
    }, [auth])

  const sendPasswordReset = useCallback(async (email: string) => {
    if (!auth) return
    await auth.sendPasswordResetEmail(auth.auth, email)
  }, [auth])

  const resendVerification = useCallback(async () => {
    const current = auth?.auth.currentUser
    if (!current) return
    await auth!.sendEmailVerification(current)
  }, [auth])

  /**
   * 使用者通常在另一個分頁點驗證信，這一頁不會自動知道。
   *
   * `reload()` 更新 emailVerified，接著<strong>強制換一個 ID token</strong>——
   * 後端看的是 token 裡的 claim，不換 token 的話後端仍然認為未驗證，
   * 使用者會遇到「畫面顯示已驗證但操作還是被擋」。
   */
  const refreshVerification = useCallback(async () => {
    const current = auth?.auth.currentUser
    if (!current) return
    await current.reload()
    await current.getIdToken(true)
    setVerificationTick((tick) => tick + 1)
  }, [auth])

  const signOut = useCallback(async () => {
    if (!auth) return
    await auth.signOut(auth.auth)
  }, [auth])

  const value = useMemo<AuthState>(() => ({
    email: user?.email ?? null,
    emailVerified: user?.emailVerified ?? false,
    loading,
    signIn,
    signInWithPassword,
    registerWithPassword,
    sendPasswordReset,
    resendVerification,
    refreshVerification,
    signOut,
    authHeaders: async (): Promise<Record<string, string>> => {
      if (!user) return {}
      // Firebase SDK 會自行快取並在過期前更新 token，這裡不需要自己管
      return { Authorization: `Bearer ${await user.getIdToken()}` }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }), [user, loading, verificationTick, signIn, signInWithPassword, registerWithPassword,
    sendPasswordReset, resendVerification, refreshVerification, signOut])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

/** 動態載入 Firebase SDK：沒設定 Firebase 時完全不會進到打包結果裡。 */
async function loadFirebaseAuth() {
  const [{ initializeApp }, authModule] = await Promise.all([
    import('firebase/app'),
    import('firebase/auth'),
  ])
  const app = initializeApp({
    apiKey: FIREBASE_CONFIG.apiKey!,
    authDomain: FIREBASE_CONFIG.authDomain!,
    projectId: FIREBASE_CONFIG.projectId!,
  })
  return {
    auth: authModule.getAuth(app),
    onAuthStateChanged: authModule.onAuthStateChanged,
    signInWithPopup: authModule.signInWithPopup,
    GoogleAuthProvider: authModule.GoogleAuthProvider,
    signInWithEmailAndPassword: authModule.signInWithEmailAndPassword,
    createUserWithEmailAndPassword: authModule.createUserWithEmailAndPassword,
    sendEmailVerification: authModule.sendEmailVerification,
    sendPasswordResetEmail: authModule.sendPasswordResetEmail,
    updateProfile: authModule.updateProfile,
    signOut: authModule.signOut,
  }
}
