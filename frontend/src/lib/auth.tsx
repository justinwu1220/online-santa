import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { AuthContext, FIREBASE_CONFIG, usingFirebase } from './authContext'
import type { AuthState } from './authContext'

/**
 * 身分驗證。
 *
 * 有兩種模式，依 Firebase 設定是否齊全自動選擇——與後端的 `dev-auth` profile 對稱：
 *
 * - **Firebase 模式**：設定了 `VITE_FIREBASE_*` 時啟用，走真正的登入流程，
 *   請求帶 `Authorization: Bearer <ID token>`
 * - **開發模式**：沒有 Firebase 設定時啟用，直接輸入 email 即可切換身分，
 *   請求帶 `X-Dev-User-Email`
 *
 * 開發模式的存在讓整個專案不需要任何雲端資源就能跑起來。它只在後端也啟用
 * `dev-auth` 時有效——正式環境的後端會直接回 401。
 */

const DEV_IDENTITY_KEY = 'online-santa.dev-identity'

export function AuthProvider({ children }: { children: ReactNode }) {
  return usingFirebase
    ? <FirebaseAuthProvider>{children}</FirebaseAuthProvider>
    : <DevAuthProvider>{children}</DevAuthProvider>
}

// ---------------------------------------------------------------- 開發模式

function DevAuthProvider({ children }: { children: ReactNode }) {
  const [email, setEmail] = useState<string | null>(() => {
    try {
      return localStorage.getItem(DEV_IDENTITY_KEY)
    } catch {
      // 無痕視窗或封鎖了網站資料時讀不到，當作未登入即可
      return null
    }
  })

  const signIn = useCallback(async (nextEmail?: string) => {
    const trimmed = nextEmail?.trim()
    if (!trimmed) return
    setEmail(trimmed)
    try {
      localStorage.setItem(DEV_IDENTITY_KEY, trimmed)
    } catch {
      // 記不住就算了，重新整理後再輸入一次
    }
  }, [])

  const signOut = useCallback(async () => {
    setEmail(null)
    try {
      localStorage.removeItem(DEV_IDENTITY_KEY)
    } catch {
      // 同上
    }
  }, [])

  const value = useMemo<AuthState>(() => ({
    email,
    loading: false,
    signIn,
    signOut,
    authHeaders: async (): Promise<Record<string, string>> =>
      email ? { 'X-Dev-User-Email': email } : {},
  }), [email, signIn, signOut])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// ---------------------------------------------------------------- Firebase 模式

type FirebaseUser = { email: string | null; getIdToken: (force?: boolean) => Promise<string> }

function FirebaseAuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<FirebaseUser | null>(null)
  const [loading, setLoading] = useState(true)
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

  const signOut = useCallback(async () => {
    if (!auth) return
    await auth.signOut(auth.auth)
  }, [auth])

  const value = useMemo<AuthState>(() => ({
    email: user?.email ?? null,
    loading,
    signIn,
    signOut,
    authHeaders: async (): Promise<Record<string, string>> => {
      if (!user) return {}
      // Firebase SDK 會自行快取並在過期前更新 token，這裡不需要自己管
      return { Authorization: `Bearer ${await user.getIdToken()}` }
    },
  }), [user, loading, signIn, signOut])

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
    signOut: authModule.signOut,
  }
}
