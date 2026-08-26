/**
 * 後端 API 的 fetch 包裝。
 *
 * M3 導入 Firebase Auth 後，會在此處自動附加 ID token，並於 401 時
 * 觸發 token refresh 後重試一次。目前先保留掛載點。
 */

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

export class ApiError extends Error {
  readonly status: number
  readonly detail: string
  readonly body?: unknown

  constructor(status: number, detail: string, body?: unknown) {
    super(detail)
    this.name = 'ApiError'
    this.status = status
    this.detail = detail
    this.body = body
  }

  /** 願望在送出認領前的一瞬間被別人領走了 */
  get isConflict() {
    return this.status === 409
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      ...init.headers,
    },
  })

  if (!response.ok) {
    // 後端統一以 RFC 7807 ProblemDetail 回覆錯誤
    const body = await response.json().catch(() => undefined)
    const detail =
      (body as { detail?: string } | undefined)?.detail ?? response.statusText
    throw new ApiError(response.status, detail, body)
  }

  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body: body ? JSON.stringify(body) : undefined }),
  patch: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'PATCH', body: JSON.stringify(body) }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}
