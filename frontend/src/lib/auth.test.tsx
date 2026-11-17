// @vitest-environment jsdom
import { act, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from './auth'
import { currentAuthHeaders, recoverFromUnauthorized, useAuth, registerAuthHeaderProvider } from './authContext'

const fetchMock = vi.fn()

/** 後端登入、註冊、換發的成功回應。 */
function session(overrides: Partial<{ accessToken: string; expiresIn: number; email: string; emailVerified: boolean }> = {}) {
  return {
    accessToken: 'access-1', expiresIn: 900, email: 'me@example.com', emailVerified: false, ...overrides,
  }
}

function respond(status: number, body?: unknown) {
  fetchMock.mockResolvedValueOnce({
    ok: status >= 200 && status < 300,
    status,
    statusText: 'x',
    json: async () => body,
    text: async () => (body === undefined ? '' : JSON.stringify(body)),
  })
}

let latest: ReturnType<typeof useAuth>

function Probe() {
  latest = useAuth()
  return (
    <div>
      <span data-testid="loading">{String(latest.loading)}</span>
      <span data-testid="email">{latest.email ?? '(none)'}</span>
      <span data-testid="verified">{String(latest.emailVerified)}</span>
    </div>
  )
}

function mount() {
  return render(<AuthProvider><Probe /></AuthProvider>)
}

describe('AuthProvider（帳號模式）', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
  })
  afterEach(() => vi.unstubAllGlobals())

  it('頁面載入時用 cookie 換發：成功就還原登入狀態', async () => {
    respond(200, session({ emailVerified: true }))
    mount()

    await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))
    expect(screen.getByTestId('email')).toHaveTextContent('me@example.com')
    expect(screen.getByTestId('verified')).toHaveTextContent('true')

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/auth/refresh')
    // cookie 要靠 credentials 才送得出去；換發請求絕不能帶 Authorization
    expect(init.credentials).toBe('include')
    expect(init.headers.Authorization).toBeUndefined()
  })

  it('沒有 cookie（換發回 401）：視為沒登入，不報錯', async () => {
    respond(401, { detail: '請先登入' })
    mount()

    await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))
    expect(screen.getByTestId('email')).toHaveTextContent('(none)')
    expect(await currentAuthHeaders()).toEqual({})
  })

  it('登入成功：email 與驗證狀態更新，請求標頭帶上 access token', async () => {
    respond(401) // 載入時沒有 cookie
    mount()
    await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))
    registerAuthHeaderProvider(latest.authHeaders)

    respond(200, session({ accessToken: 'tok-abc' }))
    await act(() => latest.signInWithPassword('me@example.com', 'correct-horse-1'))

    expect(screen.getByTestId('email')).toHaveTextContent('me@example.com')
    expect(await currentAuthHeaders()).toEqual({ Authorization: 'Bearer tok-abc' })
    const [url, init] = fetchMock.mock.calls[1]
    expect(url).toBe('/api/auth/login')
    expect(JSON.parse(init.body)).toEqual({ email: 'me@example.com', password: 'correct-horse-1' })
  })

  it('登入失敗：丟出帶 errorCode 的 ApiError，狀態不變', async () => {
    respond(401)
    mount()
    await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))

    respond(401, { detail: '信箱或密碼不正確', errorCode: 'INVALID_CREDENTIALS' })
    await expect(act(() => latest.signInWithPassword('me@example.com', 'wrong'))).rejects
      .toMatchObject({ status: 401, errorCode: 'INVALID_CREDENTIALS' })
    expect(screen.getByTestId('email')).toHaveTextContent('(none)')
  })

  it('註冊：一次帶齊名稱、真實姓名與須知同意，直接登入但信箱未驗證', async () => {
    respond(401)
    mount()
    await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))

    respond(201, session({ emailVerified: false }))
    await act(() => latest.registerWithPassword(
      'me@example.com', 'correct-horse-1', '小明', '王小明', { type: 'DONOR', version: 'v8' }))

    expect(screen.getByTestId('verified')).toHaveTextContent('false')
    const [url, init] = fetchMock.mock.calls[1]
    expect(url).toBe('/api/auth/register')
    expect(JSON.parse(init.body)).toEqual({
      email: 'me@example.com', password: 'correct-horse-1', displayName: '小明', name: '王小明',
      consent: { type: 'DONOR', version: 'v8' },
    })
  })

  it('登出：連同 cookie 一起撤銷，狀態清空', async () => {
    respond(200, session())
    mount()
    await waitFor(() => expect(screen.getByTestId('email')).toHaveTextContent('me@example.com'))

    registerAuthHeaderProvider(latest.authHeaders)
    respond(204)
    await act(() => latest.signOut())

    expect(screen.getByTestId('email')).toHaveTextContent('(none)')
    expect(fetchMock.mock.calls[1][0]).toBe('/api/auth/logout')
    expect(await currentAuthHeaders()).toEqual({})
  })

  it('登出時後端連不上：這個瀏覽器照樣登出', async () => {
    respond(200, session())
    mount()
    await waitFor(() => expect(screen.getByTestId('email')).toHaveTextContent('me@example.com'))

    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))
    await act(() => latest.signOut())

    expect(screen.getByTestId('email')).toHaveTextContent('(none)')
  })

  it('access token 快過期：下一個請求先換發，用新的 token', async () => {
    respond(200, session({ accessToken: 'about-to-expire', expiresIn: 10 }))
    mount()
    await waitFor(() => expect(screen.getByTestId('email')).toHaveTextContent('me@example.com'))
    registerAuthHeaderProvider(latest.authHeaders)

    respond(200, session({ accessToken: 'fresh' }))

    expect(await currentAuthHeaders()).toEqual({ Authorization: 'Bearer fresh' })
  })

  it('同時好幾個請求都發現 token 快過期：只換發一次（換發會輪替 cookie）', async () => {
    respond(200, session({ accessToken: 'about-to-expire', expiresIn: 10 }))
    mount()
    await waitFor(() => expect(screen.getByTestId('email')).toHaveTextContent('me@example.com'))
    registerAuthHeaderProvider(latest.authHeaders)

    respond(200, session({ accessToken: 'fresh' }))
    const results = await Promise.all([currentAuthHeaders(), currentAuthHeaders(), currentAuthHeaders()])

    expect(results.every((headers) => headers.Authorization === 'Bearer fresh')).toBe(true)
    expect(fetchMock).toHaveBeenCalledTimes(2) // 載入時一次、這次一次
  })

  it('API 回 401 時換發：成功回 true，失敗回 false 並登出', async () => {
    respond(200, session())
    mount()
    await waitFor(() => expect(screen.getByTestId('email')).toHaveTextContent('me@example.com'))

    respond(200, session({ accessToken: 'second' }))
    expect(await act(() => recoverFromUnauthorized())).toBe(true)

    respond(401, { detail: '請先登入' })
    expect(await act(() => recoverFromUnauthorized())).toBe(false)
    await waitFor(() => expect(screen.getByTestId('email')).toHaveTextContent('(none)'))
  })

  it('驗證信箱：送出驗證碼，成功後 emailVerified 變 true', async () => {
    respond(200, session({ emailVerified: false }))
    mount()
    await waitFor(() => expect(screen.getByTestId('email')).toHaveTextContent('me@example.com'))
    registerAuthHeaderProvider(latest.authHeaders)

    respond(204)
    await act(() => latest.verifyEmail('123456'))

    expect(screen.getByTestId('verified')).toHaveTextContent('true')
    const [url, init] = fetchMock.mock.calls[1]
    expect(url).toBe('/api/auth/verify-email')
    expect(JSON.parse(init.body)).toEqual({ code: '123456' })
    expect(init.headers.Authorization).toBe('Bearer access-1')
  })

  it('驗證碼錯誤：丟出錯誤，emailVerified 維持 false', async () => {
    respond(200, session({ emailVerified: false }))
    mount()
    await waitFor(() => expect(screen.getByTestId('email')).toHaveTextContent('me@example.com'))
    registerAuthHeaderProvider(latest.authHeaders)

    respond(400, { detail: '驗證碼不正確，還可以再試 4 次', errorCode: 'CODE_INVALID' })
    await expect(act(() => latest.verifyEmail('000000'))).rejects.toMatchObject({ errorCode: 'CODE_INVALID' })

    expect(screen.getByTestId('verified')).toHaveTextContent('false')
  })

  it('忘記密碼：不帶 token 打 forgot 端點', async () => {
    respond(401)
    mount()
    await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))

    respond(204)
    await act(() => latest.sendPasswordReset('me@example.com'))

    const [url, init] = fetchMock.mock.calls[1]
    expect(url).toBe('/api/auth/password/forgot')
    expect(JSON.parse(init.body)).toEqual({ email: 'me@example.com' })
    expect(init.headers.Authorization).toBeUndefined()
  })
})
