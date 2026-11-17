// @vitest-environment jsdom
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../components/Snowfall', () => ({ Snowfall: () => null }))

import { ResetPassword } from './ResetPassword'

const fetchMock = vi.fn()

function renderAt(url: string) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <ResetPassword />
    </MemoryRouter>,
  )
}

function respond(status: number, body?: unknown) {
  fetchMock.mockResolvedValueOnce({
    ok: status >= 200 && status < 300,
    status,
    statusText: 'x',
    json: async () => body,
  })
}

const passwordFields = () => ({
  password: screen.getByLabelText(/^新密碼/),
  confirm: screen.getByLabelText(/^確認新密碼/),
})

describe('ResetPassword（信裡的重設密碼連結）', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
  })
  afterEach(() => vi.unstubAllGlobals())

  it('連結上沒有 token：直接說連結失效，不給填新密碼', () => {
    renderAt('/reset-password')
    expect(screen.getByText('這個連結已經失效')).toBeInTheDocument()
    expect(screen.queryByLabelText(/^新密碼/)).not.toBeInTheDocument()
  })

  it('密碼太短、兩次不一樣：擋在前端，不打 API', async () => {
    const user = userEvent.setup()
    renderAt('/reset-password?token=abc')
    const { password, confirm } = passwordFields()

    await user.type(password, '1234567')
    await user.type(confirm, '12345678')
    await user.click(screen.getByRole('button', { name: '設定新密碼' }))
    expect(screen.getByText('密碼至少要 8 個字元')).toBeInTheDocument()

    await user.clear(password)
    await user.type(password, 'long-enough-1')
    await user.click(screen.getByRole('button', { name: '設定新密碼' }))
    expect(screen.getByText('兩次輸入的密碼不一樣')).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('成功：帶著 token 與新密碼打重設端點，顯示完成並引導去登入', async () => {
    const user = userEvent.setup()
    respond(204)
    renderAt('/reset-password?token=tok-123')
    const { password, confirm } = passwordFields()

    await user.type(password, 'brand-new-pass-9')
    await user.type(confirm, 'brand-new-pass-9')
    await user.click(screen.getByRole('button', { name: '設定新密碼' }))

    expect(await screen.findByText('密碼已重設')).toBeInTheDocument()
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/auth/password/reset')
    expect(JSON.parse(init.body)).toEqual({ token: 'tok-123', newPassword: 'brand-new-pass-9' })
    expect(screen.getByRole('link', { name: '前往登入' })).toHaveAttribute('href', '/login')
  })

  it('後端說連結失效（用過或過期）：換成失效畫面', async () => {
    const user = userEvent.setup()
    respond(400, { detail: '連結失效', errorCode: 'TOKEN_INVALID' })
    renderAt('/reset-password?token=used')
    const { password, confirm } = passwordFields()

    await user.type(password, 'brand-new-pass-9')
    await user.type(confirm, 'brand-new-pass-9')
    await user.click(screen.getByRole('button', { name: '設定新密碼' }))

    await waitFor(() => expect(screen.getByText('這個連結已經失效')).toBeInTheDocument())
  })

  it('其他錯誤：留在表單上顯示原因，輸入的內容還在', async () => {
    const user = userEvent.setup()
    respond(400, { detail: '請求內容有誤', errorCode: 'VALIDATION_FAILED', fieldErrors: { newPassword: '密碼長度需為 8 到 128 個字元' } })
    renderAt('/reset-password?token=abc')
    const { password, confirm } = passwordFields()

    await user.type(password, 'brand-new-pass-9')
    await user.type(confirm, 'brand-new-pass-9')
    await user.click(screen.getByRole('button', { name: '設定新密碼' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('密碼長度需為 8 到 128 個字元')
    expect(passwordFields().password).toHaveValue('brand-new-pass-9')
  })
})
