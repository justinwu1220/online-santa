import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError } from './api'

const authState = vi.hoisted(() => ({ headers: {} as Record<string, string>, recover: vi.fn() }))

vi.mock('./authContext', () => ({
  currentAuthHeaders: async () => authState.headers,
  recoverFromUnauthorized: authState.recover,
}))

function respondWith(body: string | null, init: ResponseInit) {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(body, init)))
}

afterEach(() => {
  vi.unstubAllGlobals()
  authState.headers = {}
  authState.recover.mockReset()
})

describe('api（回應內容的處理）', () => {
  it('200 空內容（後端回 void 的端點）視為成功', async () => {
    respondWith('', { status: 200 })
    await expect(api.post('/api/admin/claims/1/release', { note: '違規' })).resolves.toBeUndefined()
  })

  it('204 視為成功', async () => {
    respondWith(null, { status: 204 })
    await expect(api.delete('/api/x')).resolves.toBeUndefined()
  })

  it('200 JSON 會解析', async () => {
    respondWith(JSON.stringify({ id: 'a' }), { status: 200 })
    await expect(api.get('/api/x')).resolves.toEqual({ id: 'a' })
  })

  it('錯誤回應丟出 ApiError，帶後端的 detail', async () => {
    respondWith(JSON.stringify({ detail: '只有還沒寄出的認領能釋出', errorCode: 'CLAIM_NOT_RELEASABLE' }), { status: 409 })
    const error = await api.post('/api/x').catch((e) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 409, message: '只有還沒寄出的認領能釋出' })
  })
})

describe('api（access token 過期時的換發重試）', () => {
  /** 依序回應；每次呼叫 fetch 取下一個 */
  function respondInOrder(...responses: Response[]) {
    const fetchMock = vi.fn(async () => responses.shift()!)
    vi.stubGlobal('fetch', fetchMock)
    return fetchMock
  }

  it('帶了 token 卻收到 401：換發成功就用新 token 重試一次', async () => {
    authState.headers = { Authorization: 'Bearer old' }
    authState.recover.mockImplementation(async () => {
      authState.headers = { Authorization: 'Bearer new' }
      return true
    })
    const fetchMock = respondInOrder(
      new Response(JSON.stringify({ detail: '請先登入' }), { status: 401 }),
      new Response(JSON.stringify({ id: 'a' }), { status: 200 }))

    await expect(api.get('/api/me')).resolves.toEqual({ id: 'a' })

    expect(fetchMock).toHaveBeenCalledTimes(2)
    const retryHeaders = (fetchMock.mock.calls[1] as unknown as [string, RequestInit])[1].headers as Record<string, string>
    expect(retryHeaders.Authorization).toBe('Bearer new')
  })

  it('換發失敗（登入狀態已失效）：不重試，照樣丟出 401', async () => {
    authState.headers = { Authorization: 'Bearer old' }
    authState.recover.mockResolvedValue(false)
    const fetchMock = respondInOrder(new Response(JSON.stringify({ detail: '請先登入' }), { status: 401 }))

    const error = await api.get('/api/me').catch((e) => e)

    expect(error).toMatchObject({ status: 401 })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('重試後還是 401：不再換發，避免無限迴圈', async () => {
    authState.headers = { Authorization: 'Bearer old' }
    authState.recover.mockResolvedValue(true)
    const fetchMock = respondInOrder(
      new Response(JSON.stringify({ detail: '請先登入' }), { status: 401 }),
      new Response(JSON.stringify({ detail: '請先登入' }), { status: 401 }))

    await expect(api.get('/api/me')).rejects.toMatchObject({ status: 401 })

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(authState.recover).toHaveBeenCalledTimes(1)
  })

  it('沒登入（沒帶 token）的 401 不會去換發', async () => {
    authState.headers = {}
    const fetchMock = respondInOrder(new Response(JSON.stringify({ detail: '請先登入' }), { status: 401 }))

    await expect(api.get('/api/me')).rejects.toMatchObject({ status: 401 })

    expect(authState.recover).not.toHaveBeenCalled()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('非 401 的錯誤不會換發', async () => {
    authState.headers = { Authorization: 'Bearer old' }
    respondInOrder(new Response(JSON.stringify({ detail: '無權' }), { status: 403 }))

    await expect(api.get('/api/admin/x')).rejects.toMatchObject({ status: 403 })

    expect(authState.recover).not.toHaveBeenCalled()
  })
})
