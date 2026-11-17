import { createContext, useContext } from 'react'

/**
 * 身分驗證的型別與 context。
 *
 * 與 `auth.tsx` 分開是為了 fast refresh——同一個檔案混著元件與非元件的匯出時，
 * 熱更新會退化成整頁重載。
 */

/**
 * 開發模式：不用密碼，輸入任何 email 就能切換身分（後端要同時啟用 `dev-auth` profile，
 * 靠 `X-Dev-User-Email` 標頭認人）。只有明確設定 `VITE_DEV_AUTH=true` 才會啟用，
 * 沒設定就是真正的帳號登入——正式環境不可能不小心落入開發模式。
 */
export const devAuth = import.meta.env.VITE_DEV_AUTH === 'true'

/** 走真正的帳號流程（密碼、信箱驗證碼、忘記密碼）。 */
export const usingLocalAuth = !devAuth

export interface AuthState {
  /** 目前登入者的 email，未登入為 null */
  email: string | null
  /**
   * 信箱是否已驗證。註冊之後要輸入寄到信箱的 6 位數驗證碼才會變成 true。
   * 後端會擋下未驗證帳號的認領與機構申請。
   */
  emailVerified: boolean
  loading: boolean

  /** 只在開發模式有意義：以 email 指定身分 */
  signIn: (email?: string) => Promise<void>
  signInWithPassword: (email: string, password: string) => Promise<void>
  /** 註冊、直接登入，並自動寄出 6 位數的信箱驗證碼 */
  registerWithPassword: (
    email: string, password: string, displayName?: string, name?: string,
    /** 使用者在註冊前同意的須知（種類與版本）；有帶就在建好帳號後一併寫入同意紀錄 */
    consent?: { type: 'DONOR' | 'ORG'; version: string },
  ) => Promise<void>
  /** 寄出重設密碼的信（不論信箱有沒有註冊過都會成功，不透露帳號是否存在） */
  sendPasswordReset: (email: string) => Promise<void>
  /** 重新寄出信箱驗證碼 */
  resendVerification: () => Promise<void>
  /** 輸入信箱驗證碼。錯誤時丟出 ApiError（CODE_INVALID／CODE_EXPIRED／CODE_LOCKED） */
  verifyEmail: (code: string) => Promise<void>
  signOut: () => Promise<void>

  /** 每次請求呼叫，回傳要附加的驗證標頭 */
  authHeaders: () => Promise<Record<string, string>>

  /**
   * 只在開發模式有意義：模擬「信箱尚未驗證」的狀態。
   *
   * 沒有這個開關的話，本機無法快速檢查驗證橫幅與被擋下的操作。
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
 * API 回 401（access token 剛好過期）時，api 模組用這個請 AuthProvider 換發一次再重試。
 * 回傳 true 代表換發成功、可以重試；false 代表登入狀態已經失效。
 */
let unauthorizedHandler: (() => Promise<boolean>) | null = null

export function registerUnauthorizedHandler(handler: () => Promise<boolean>) {
  unauthorizedHandler = handler
}

export async function recoverFromUnauthorized(): Promise<boolean> {
  return unauthorizedHandler ? unauthorizedHandler() : false
}

/**
 * 後端帳號端點的錯誤碼對中文訊息。
 *
 * 沒列在這裡的錯誤碼直接用後端回的 `detail`——驗證碼剩餘次數、鎖定時間這類訊息是
 * 後端組好的中文，前端不重寫。登入失敗一律是「信箱或密碼不正確」，不區分帳號不存在與
 * 密碼錯誤，避免有人拿登入表單去探測哪些信箱在這個平台上有帳號。
 */
const AUTH_ERROR_MESSAGES: Record<string, string> = {
  INVALID_CREDENTIALS: '信箱或密碼不正確',
  EMAIL_TAKEN: '這個信箱已經註冊過了，請直接登入',
  ORIGIN_NOT_ALLOWED: '請從官方網站登入',
}

interface ProblemLike {
  status?: number
  detail?: string
  errorCode?: string
  fieldErrors?: Record<string, string>
}

export function describeAuthError(error: unknown): string {
  const problem = error as ProblemLike | undefined
  if (problem?.errorCode && AUTH_ERROR_MESSAGES[problem.errorCode]) {
    return AUTH_ERROR_MESSAGES[problem.errorCode]
  }
  // 欄位驗證不過（例如密碼太短）：顯示第一個欄位的訊息
  const firstFieldError = problem?.fieldErrors && Object.values(problem.fieldErrors)[0]
  if (firstFieldError) return firstFieldError
  if (error instanceof TypeError) {
    return '網路連線有問題，請檢查後再試'
  }
  if (problem?.detail) return problem.detail
  if (error instanceof Error && error.message) return error.message
  return '登入時發生問題，請稍後再試'
}
