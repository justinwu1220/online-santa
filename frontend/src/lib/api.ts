import { currentAuthHeaders, recoverFromUnauthorized } from './authContext'

/**
 * 後端 API 的 fetch 包裝。
 *
 * 每次請求向 AuthProvider 取得驗證標頭——帳號模式是 access token，
 * 開發模式是 X-Dev-User-Email，呼叫端不需要知道差別。
 *
 * access token 只有 15 分鐘。請求送出時它剛好過期（或在別的分頁已被換掉）會收到 401，
 * 這時請 AuthProvider 用 cookie 換發一次並重試——使用者不該因為閒置一陣子就被丟回登入頁。
 */

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

export class ApiError extends Error {
  readonly status: number
  readonly detail: string
  readonly errorCode?: string
  readonly fieldErrors?: Record<string, string>

  constructor(status: number, detail: string, body?: unknown) {
    super(detail)
    this.name = 'ApiError'
    this.status = status
    this.detail = detail

    const problem = body as
      | { errorCode?: string; fieldErrors?: Record<string, string> }
      | undefined
    this.errorCode = problem?.errorCode
    this.fieldErrors = problem?.fieldErrors
  }

  /** 願望在送出認領前的一瞬間被別人領走了 */
  get isAlreadyClaimed() {
    return this.errorCode === 'WISH_ALREADY_CLAIMED'
  }

  get isUnauthenticated() {
    return this.status === 401
  }

  /** 找不到，或無權存取——後端刻意用 404 而非 403，避免洩漏資源是否存在 */
  get isNotFound() {
    return this.status === 404
  }
}

async function request<T>(path: string, init: RequestInit = {}, retried = false): Promise<T> {
  const authHeaders = await currentAuthHeaders()
  const response = await fetch(`${BASE_URL}${path}`, {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      ...authHeaders,
      ...init.headers,
    },
  })

  // 只有「帶了 token 卻被拒絕」才值得換發重試；沒登入的訪客收到 401 換發也沒用
  if (response.status === 401 && !retried && 'Authorization' in authHeaders
    && await recoverFromUnauthorized()) {
    return request<T>(path, init, true)
  }

  if (!response.ok) {
    // 後端統一以 RFC 7807 ProblemDetail 回覆錯誤
    const body = await response.json().catch(() => undefined)
    const detail =
      (body as { detail?: string } | undefined)?.detail ?? response.statusText
    throw new ApiError(response.status, detail, body)
  }

  // 204 或回 void 的端點（200 空內容）都沒有 JSON 可解析
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }),
  put: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  patch: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'PATCH', body: JSON.stringify(body) }),
  delete: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'DELETE', body: body === undefined ? undefined : JSON.stringify(body) }),
}

/** 組出帶查詢參數的路徑，略過未設定的值。 */
export function withQuery(path: string, params: Record<string, string | number | undefined>) {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') {
      query.set(key, String(value))
    }
  }
  const queryString = query.toString()
  return queryString ? `${path}?${queryString}` : path
}
