// @vitest-environment jsdom
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../lib/api'

const auth = { verifyEmail: vi.fn() }

vi.mock('../../lib/authContext', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../lib/authContext')>()),
  useAuth: () => auth,
}))

import { VerifyCodeInput } from './VerifyCodeInput'

const cells = () => screen.getAllByRole('textbox') as HTMLInputElement[]

describe('VerifyCodeInput（6 位數信箱驗證碼）', () => {
  beforeEach(() => {
    auth.verifyEmail.mockReset()
    auth.verifyEmail.mockResolvedValue(undefined)
  })

  it('有 6 格，一格一個數字', () => {
    render(<VerifyCodeInput />)
    expect(cells()).toHaveLength(6)
  })

  it('逐格輸入，填滿 6 位數就自動送出，成功後通知呼叫端', async () => {
    const user = userEvent.setup()
    const onVerified = vi.fn()
    render(<VerifyCodeInput onVerified={onVerified} autoFocus />)

    await user.keyboard('123456')

    await waitFor(() => expect(auth.verifyEmail).toHaveBeenCalledWith('123456'))
    await waitFor(() => expect(onVerified).toHaveBeenCalledTimes(1))
  })

  it('沒填滿不會送出', async () => {
    const user = userEvent.setup()
    render(<VerifyCodeInput autoFocus />)

    await user.keyboard('12345')

    expect(auth.verifyEmail).not.toHaveBeenCalled()
  })

  it('整串貼上，自動分到各格並送出', async () => {
    const user = userEvent.setup()
    render(<VerifyCodeInput />)

    await user.click(cells()[0])
    await user.paste('987654')

    await waitFor(() => expect(auth.verifyEmail).toHaveBeenCalledWith('987654'))
    expect(cells().map((cell) => cell.value).join('')).toBe('987654')
  })

  it('貼上的內容夾雜空白與符號，只取數字', async () => {
    const user = userEvent.setup()
    render(<VerifyCodeInput />)

    await user.click(cells()[0])
    await user.paste(' 123-456 ')

    await waitFor(() => expect(auth.verifyEmail).toHaveBeenCalledWith('123456'))
  })

  it('不是數字的輸入被忽略', async () => {
    const user = userEvent.setup()
    render(<VerifyCodeInput autoFocus />)

    await user.keyboard('ab12')

    expect(cells().map((cell) => cell.value).join('')).toBe('12')
  })

  it('驗證失敗：顯示後端說的原因、清空重填，不通知呼叫端', async () => {
    const user = userEvent.setup()
    const onVerified = vi.fn()
    auth.verifyEmail.mockRejectedValue(
      new ApiError(400, '驗證碼不正確，還可以再試 4 次', { errorCode: 'CODE_INVALID' }))
    render(<VerifyCodeInput onVerified={onVerified} autoFocus />)

    await user.keyboard('111111')

    expect(await screen.findByRole('alert')).toHaveTextContent('還可以再試 4 次')
    expect(cells().map((cell) => cell.value).join('')).toBe('')
    expect(onVerified).not.toHaveBeenCalled()
  })

  it('開始重新輸入就清掉先前的錯誤', async () => {
    const user = userEvent.setup()
    auth.verifyEmail.mockRejectedValueOnce(new ApiError(400, '驗證碼已經失效', { errorCode: 'CODE_EXPIRED' }))
    render(<VerifyCodeInput autoFocus />)

    await user.keyboard('111111')
    await screen.findByRole('alert')
    await user.click(cells()[0])
    await user.keyboard('2')

    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('Backspace 在空格上會退回前一格並清掉它', async () => {
    const user = userEvent.setup()
    render(<VerifyCodeInput autoFocus />)

    await user.keyboard('12')
    await user.keyboard('{Backspace}') // 清掉第 2 格
    await user.keyboard('{Backspace}') // 第 2 格已空：退回第 1 格並清掉

    expect(cells().map((cell) => cell.value).join('')).toBe('')
    expect(cells()[0]).toHaveFocus()
  })
})
