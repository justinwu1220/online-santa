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
  /**
   * 信箱是否已驗證。
   *
   * Google 登入永遠是 true；密碼註冊在使用者點驗證信之前是 false。
   * 後端會擋下未驗證帳號的認領與機構申請。
   */
  emailVerified: boolean
  loading: boolean

  /** Google 登入（或開發模式下以 email 指定身分） */
  signIn: (email?: string) => Promise<void>
  signInWithPassword: (email: string, password: string) => Promise<void>
  /** 註冊並自動寄出驗證信 */
  registerWithPassword: (email: string, password: string, displayName?: string) => Promise<void>
  sendPasswordReset: (email: string) => Promise<void>
  resendVerification: () => Promise<void>
  /** 使用者在另一個分頁點完驗證信後，重新讀取狀態 */
  refreshVerification: () => Promise<void>
  signOut: () => Promise<void>

  /** 每次請求呼叫，回傳要附加的驗證標頭 */
  authHeaders: () => Promise<Record<string, string>>

  /**
   * 只在開發模式有意義：模擬「信箱尚未驗證」的狀態。
   *
   * 沒有這個開關的話，本機無法檢查驗證橫幅與被擋下的操作——那正是這次改動的重點，
   * 不該只能在有 Firebase 專案時才測得到。
   */
  devUnverified?: boolean
  setDevUnverified?: (next: boolean) => void
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

/**
 * Firebase 錯誤碼對中文訊息。
 *
 * 訊息刻意<strong>不精確</strong>：新的 Firebase 專案預設開啟 email enumeration
 * protection，帳號不存在與密碼錯誤會統一回 `invalid-credential`。那是刻意的設計
 * ——避免有人拿註冊表單去探測哪些信箱在這個平台上有帳號。我們照著它的語氣走。
 */
const AUTH_ERROR_MESSAGES: Record<string, string> = {
  'auth/invalid-credential': '信箱或密碼不正確',
  'auth/invalid-login-credentials': '信箱或密碼不正確',
  'auth/wrong-password': '信箱或密碼不正確',
  'auth/user-not-found': '信箱或密碼不正確',
  'auth/email-already-in-use': '這個信箱已經註冊過了，請直接登入',
  'auth/weak-password': '密碼至少要 6 個字元，建議混合英文與數字',
  'auth/invalid-email': '信箱格式不正確',
  'auth/missing-password': '請輸入密碼',
  'auth/too-many-requests': '嘗試次數過多，請稍等幾分鐘再試',
  'auth/network-request-failed': '網路連線有問題，請檢查後再試',
  'auth/popup-closed-by-user': '登入視窗被關閉了，請再試一次',
  'auth/popup-blocked': '瀏覽器擋掉了登入視窗，請允許彈出視窗後再試',
  'auth/user-disabled': '這個帳號已被停用',
  // 同一個信箱先用密碼註冊、後來又用 Google 登入時可能出現
  'auth/account-exists-with-different-credential':
    '這個信箱已經用另一種方式註冊過了，請改用原本的方式登入',
}

export function describeAuthError(error: unknown): string {
  const code = (error as { code?: string } | undefined)?.code
  if (code && AUTH_ERROR_MESSAGES[code]) {
    return AUTH_ERROR_MESSAGES[code]
  }
  if (error instanceof Error && error.message) {
    return error.message
  }
  return '登入時發生問題，請稍後再試'
}
