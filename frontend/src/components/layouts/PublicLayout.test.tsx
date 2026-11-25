// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PublicLayout } from './PublicLayout'

const state = { role: 'DONOR', permitNo: null as string | null }

vi.mock('../../lib/authContext', () => ({
  usingLocalAuth: true,
  useAuth: () => ({ email: 'me@example.com', loading: false, signOut: vi.fn() }),
}))
vi.mock('../../lib/useCurrentUser', () => ({
  useCurrentUser: () => ({ data: { role: state.role, emailVerified: true } }),
  effectiveRoleOf: (user: { role: string } | undefined) => user?.role ?? 'DONOR',
}))
vi.mock('../../lib/useMyProfile', () => ({ useMyProfile: () => ({ data: { displayName: '熱心民眾' } }) }))
vi.mock('../../lib/api', () => ({
  api: {
    get: vi.fn(async (path: string) =>
      (path.startsWith('/api/campaigns') ? { permitNo: state.permitNo } : { content: [] })),
  },
}))
// 與頁首右上角無關的區塊：換成空元件，只測頁首
vi.mock('../Snowfall', () => ({ Snowfall: () => null }))
vi.mock('../onboard/OnboardHost', () => ({ OnboardHost: ({ children }: { children: React.ReactNode }) => <>{children}</> }))
vi.mock('../transition/TransitionProvider', () => ({ TransitionProvider: ({ children }: { children: React.ReactNode }) => <>{children}</> }))
vi.mock('../ui/Toast', () => ({ ToastProvider: ({ children }: { children: React.ReactNode }) => <>{children}</> }))
vi.mock('../ui/VerifyBar', () => ({ VerifyBar: () => null }))
vi.mock('../ui/SuspendedBar', () => ({ SuspendedBar: () => null }))
vi.mock('../terms/TermsGate', () => ({ TermsGate: () => null }))

function renderLayout() {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter><PublicLayout /></MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('願望牆右上角的個人資訊', () => {
  beforeEach(() => { state.role = 'DONOR'; state.permitNo = null })

  it('認領者：顯示名稱與個人資料圖示、「我的認領」，沒有後台連結', () => {
    renderLayout()
    expect(screen.getByText('熱心民眾')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '個人資料' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /我的認領/ })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '機構後台' })).toBeNull()
    expect(screen.getByRole('button', { name: '登出' })).toBeInTheDocument()
  })

  it('機構成員：不顯示個人資訊與「我的認領」，保留「機構後台」與登出', () => {
    state.role = 'ORG_MEMBER'
    renderLayout()
    expect(screen.queryByText('熱心民眾')).toBeNull()
    expect(screen.queryByRole('link', { name: '個人資料' })).toBeNull()
    expect(screen.queryByRole('link', { name: /我的認領/ })).toBeNull()
    expect(screen.getByRole('link', { name: '機構後台' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '登出' })).toBeInTheDocument()
  })

  it('管理員：不顯示個人資訊與「我的認領」，保留「管理員後台」與登出', () => {
    state.role = 'ADMIN'
    renderLayout()
    expect(screen.queryByText('熱心民眾')).toBeNull()
    expect(screen.queryByRole('link', { name: '個人資料' })).toBeNull()
    expect(screen.queryByRole('link', { name: /我的認領/ })).toBeNull()
    expect(screen.getByRole('link', { name: '管理員後台' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '登出' })).toBeInTheDocument()
  })
})

describe('頁尾', () => {
  beforeEach(() => { state.role = 'DONOR'; state.permitNo = null })

  it('顯示聯絡信箱與著作權；信箱是純文字，不做成連結', async () => {
    renderLayout()

    const footer = screen.getByRole('contentinfo')
    expect(await within(footer).findByText('聯絡我們：onlinesanta2026@gmail.com')).toBeInTheDocument()
    expect(within(footer).getByText('© 2026 線上聖誕老人')).toBeInTheDocument()
    expect(within(footer).queryByRole('link')).toBeNull()
  })

  it('沒有勸募許可文號就不顯示那一行', async () => {
    renderLayout()

    const footer = screen.getByRole('contentinfo')
    await within(footer).findByText(/聯絡我們/)
    expect(within(footer).queryByText(/勸募許可文號/)).toBeNull()
  })

  it('檔期有勸募許可文號時顯示出來', async () => {
    state.permitNo = '衛部救字第1150000號'
    renderLayout()

    expect(await screen.findByText('勸募許可文號：衛部救字第1150000號')).toBeInTheDocument()
  })
})
