import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError } from './api'

vi.mock('./authContext', () => ({ currentAuthHeaders: async () => ({}) }))

function respondWith(body: string | null, init: ResponseInit) {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(body, init)))
}

afterEach(() => vi.unstubAllGlobals())

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
