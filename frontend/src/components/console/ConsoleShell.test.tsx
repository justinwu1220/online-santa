// @vitest-environment jsdom
import { act, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../lib/authContext', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../lib/authContext')>()),
  useAuth: () => ({ email: 'admin@example.org', signOut: vi.fn() }),
}))
// 外殼周邊（雪花、首次說明、驗證提示列）與這裡要測的選單行為無關
vi.mock('../Snowfall', () => ({ Snowfall: () => null }))
vi.mock('../ui/VerifyBar', () => ({ VerifyBar: () => null }))
vi.mock('../onboard/OnboardHost', () => ({ OnboardHost: ({ children }: { children: React.ReactNode }) => <>{children}</> }))

import { ConsoleShell } from './ConsoleShell'

const items = [
  { to: '/admin', label: '總覽', icon: <path d="M0 0" />, end: true },
  { to: '/admin/orgs', label: '機構審核', icon: <path d="M0 0" /> },
  { to: '/admin/wishes', label: '願望', icon: <path d="M0 0" /> },
]

function Where() {
  return <span data-testid="where">{useLocation().pathname}</span>
}

function renderShell(initial = '/admin') {
  return render(
    <MemoryRouter initialEntries={[initial]}>
      <Routes>
        <Route path="/admin/*" element={
          <ConsoleShell appLabel="管理員後台" name="團隊" items={items}>
            <Where />
          </ConsoleShell>} />
      </Routes>
    </MemoryRouter>,
  )
}

const main = () => document.querySelector('.org-main') as HTMLElement
const click = (label: string) => fireEvent.click(screen.getByRole('link', { name: new RegExp(label) }))
const where = () => screen.getByTestId('where').textContent
const stranded = () => main().getAnimations().filter((a) => a.playState === 'running'
  && JSON.stringify((a.effect as unknown as { getKeyframes: () => unknown }).getKeyframes()).includes('"opacity":0}'))

beforeEach(() => { vi.useFakeTimers() })
afterEach(() => { vi.useRealTimers() })

describe('ConsoleShell 左側選單切換', () => {
  it('點選單：內容先淡出，約 120ms 後換頁', async () => {
    renderShell()
    click('願望')
    expect(where()).toBe('/admin') // 淡出期間還在原頁
    await act(async () => { await vi.advanceTimersByTimeAsync(130) })
    expect(where()).toBe('/admin/wishes')
  })

  it('換頁之後，淡出留下的「維持透明」動畫已被取消', async () => {
    renderShell()
    click('願望')
    await act(async () => { await vi.advanceTimersByTimeAsync(130) })
    // 只剩新內容的浮上動畫（由透明到可見），沒有任何淡出到透明的殘留
    expect(main().getAnimations().length).toBe(1)
    expect(stranded()).toHaveLength(0)
  })

  it('連點不同選單：只有最後一次生效，不會多換頁、也不留殘留動畫', async () => {
    renderShell()
    click('願望')
    click('機構審核')
    click('總覽') // 最後點回目前所在的頁
    await act(async () => { await vi.advanceTimersByTimeAsync(500) })
    expect(where()).toBe('/admin')
    expect(stranded()).toHaveLength(0)
  })

  it('連點兩個不同的頁：停在最後點的那一頁', async () => {
    renderShell()
    click('機構審核')
    click('願望')
    await act(async () => { await vi.advanceTimersByTimeAsync(500) })
    expect(where()).toBe('/admin/wishes')
    expect(main().getAnimations().length).toBe(1)
    expect(stranded()).toHaveLength(0)
  })

  it('點目前所在的頁：不換頁、內容維持原樣', async () => {
    renderShell()
    click('總覽')
    await act(async () => { await vi.advanceTimersByTimeAsync(300) })
    expect(where()).toBe('/admin')
    expect(main().getAnimations()).toHaveLength(0)
  })

  it('按住修飾鍵（新分頁開啟）不被攔截', () => {
    renderShell()
    fireEvent.click(screen.getByRole('link', { name: /願望/ }), { ctrlKey: true })
    expect(main().getAnimations()).toHaveLength(0)
  })
})
