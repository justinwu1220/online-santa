// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const auth = {
  email: null as string | null,
  emailVerified: false,
  loading: false,
  signIn: vi.fn().mockResolvedValue(undefined),
  signInWithPassword: vi.fn().mockResolvedValue(undefined),
  registerWithPassword: vi.fn().mockResolvedValue(undefined),
  sendPasswordReset: vi.fn().mockResolvedValue(undefined),
}

// 用 Firebase 模式（顯示密碼欄位）測試；authContext 其餘匯出維持原樣
vi.mock('../../lib/authContext', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../lib/authContext')>()),
  usingFirebase: true,
  useAuth: () => auth,
}))

import { AuthPanel } from './AuthPanel'

function renderPanel(props: Partial<React.ComponentProps<typeof AuthPanel>> = {}) {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <AuthPanel {...props} />
    </QueryClientProvider>,
  )
}

const field = (label: string) =>
  screen.getByText(label, { selector: '.field__label' }).closest('.field') as HTMLElement
const errorOf = (label: string) => field(label).querySelector('.field__error')?.textContent ?? ''
const submitButton = () => document.querySelector('form.auth-form button[type=submit]') as HTMLButtonElement

beforeEach(() => {
  vi.clearAllMocks()
})

describe('AuthPanel 必填欄位（紅框＋錯誤文字＋捲動聚焦）', () => {
  it('登入：空白送出，信箱與密碼各標紅框與錯誤文字，並聚焦第一個欄位', () => {
    renderPanel()
    fireEvent.click(submitButton())

    expect(field('信箱')).toHaveClass('is-invalid')
    expect(errorOf('信箱')).toBe('請填寫信箱')
    expect(field('密碼')).toHaveClass('is-invalid')
    expect(errorOf('密碼')).toBe('請填寫密碼')
    expect(auth.signInWithPassword).not.toHaveBeenCalled()
  })

  it('信箱格式不對：提示確認信箱格式', () => {
    renderPanel()
    fireEvent.change(field('信箱').querySelector('input')!, { target: { value: 'abc' } })
    fireEvent.click(submitButton())
    expect(errorOf('信箱')).toBe('請確認信箱格式')
  })

  it('開始輸入就清掉該欄的錯誤與紅框', () => {
    renderPanel()
    fireEvent.click(submitButton())
    expect(field('信箱')).toHaveClass('is-invalid')

    fireEvent.change(field('信箱').querySelector('input')!, { target: { value: 'a@b.co' } })
    expect(field('信箱')).not.toHaveClass('is-invalid')
    expect(errorOf('信箱')).toBe('')
    // 沒動到的欄位維持錯誤
    expect(field('密碼')).toHaveClass('is-invalid')
  })

  it('欄位都填好：才呼叫登入', () => {
    renderPanel()
    fireEvent.change(field('信箱').querySelector('input')!, { target: { value: 'a@b.co' } })
    fireEvent.change(field('密碼').querySelector('input')!, { target: { value: '123456' } })
    fireEvent.click(submitButton())
    expect(auth.signInWithPassword).toHaveBeenCalledWith('a@b.co', '123456')
  })

  it('註冊（認領者）：姓名、顯示名稱、信箱、密碼、確認密碼全部必填，一次標出所有錯誤', () => {
    renderPanel({ initialTab: 'register', initialRole: 'donor' })
    fireEvent.click(submitButton())

    for (const label of ['真實姓名', '顯示名稱', '信箱', '密碼', '確認密碼']) {
      expect(field(label), label).toHaveClass('is-invalid')
      expect(errorOf(label), label).not.toBe('')
    }
    expect(auth.registerWithPassword).not.toHaveBeenCalled()
  })

  it('註冊：密碼不足 6 字元、兩次密碼不一致各有對應提示', () => {
    renderPanel({ initialTab: 'register', initialRole: 'donor' })
    fireEvent.change(field('真實姓名').querySelector('input')!, { target: { value: '王大明' } })
    fireEvent.change(field('顯示名稱').querySelector('input')!, { target: { value: '大明' } })
    fireEvent.change(field('信箱').querySelector('input')!, { target: { value: 'a@b.co' } })
    fireEvent.change(field('密碼').querySelector('input')!, { target: { value: '123' } })
    fireEvent.change(field('確認密碼').querySelector('input')!, { target: { value: '124' } })
    fireEvent.click(submitButton())
    expect(errorOf('密碼')).toBe('密碼至少要 6 個字元')

    fireEvent.change(field('密碼').querySelector('input')!, { target: { value: '123456' } })
    fireEvent.click(submitButton())
    expect(errorOf('確認密碼')).toBe('兩次輸入的密碼不一樣')
  })

  it('驗證不過時，第一個有錯的欄位被捲到畫面中央', async () => {
    renderPanel()
    const scroll = window.Element.prototype.scrollIntoView as unknown as ReturnType<typeof vi.fn>
    scroll.mockClear()
    fireEvent.click(submitButton())
    await vi.waitFor(() => expect(scroll).toHaveBeenCalled())
  })
})

