import { createContext, useContext } from 'react'

/**
 * 身分驗證的型別與 context。
 *
 * 與 `auth.tsx` 分開是為了 fast refresh——同一個檔案混著元件與非元件的匯出時，
 * 熱更新會退化成整頁重載。
 */

export const FIREBASE_CONFIG = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
}

/** 設定齊全時走真正的 Firebase 登入，否則退回開發模式。 */
export const usingFirebase = Boolean(FIREBASE_CONFIG.apiKey && FIREBASE_CONFIG.projectId)

export interface AuthState {
  /** 目前登入者的 email，未登入為 null */
  email: string | null
  loading: boolean
  signIn: (email?: string) => Promise<void>
  signOut: () => Promise<void>
  /** 每次請求呼叫，回傳要附加的驗證標頭 */
  authHeaders: () => Promise<Record<string, string>>
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth 必須在 AuthProvider 內使用')
  }
  return context
}

/**
 * 供 api 模組取得驗證標頭。
 *
 * fetch wrapper 不是 React 元件，拿不到 context，因此由 AuthHeaderBridge 掛載時
 * 把取得標頭的函式登記在這裡。
 */
let headerProvider: (() => Promise<Record<string, string>>) | null = null

export function registerAuthHeaderProvider(provider: () => Promise<Record<string, string>>) {
  headerProvider = provider
}

export async function currentAuthHeaders(): Promise<Record<string, string>> {
  return headerProvider ? headerProvider() : {}
}
