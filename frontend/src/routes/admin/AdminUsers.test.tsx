// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../../lib/api'
import type { AdminUserDetail, AdminUserView } from '../../lib/types'
import { AdminUsers } from './AdminUsers'

const user: AdminUserView = {
  id: 'u1', email: 'donor@example.com', displayName: '違規的民眾', name: '王大明', createdAt: '2026-09-01T00:00:00Z', suspended: false,
}
const detail: AdminUserDetail = {
  user, phone: '0912-345-678', snapshot: null,
  claims: [
    { claimId: 'c1', wishId: 'w1', wishTitle: '還沒寄的', organizationName: '測試機構', status: 'CLAIMED', restriction: 'NONE', claimedAt: '2026-10-01T00:00:00Z', canRelease: true },
    { claimId: 'c2', wishId: 'w2', wishTitle: '已經寄的', organizationName: '測試機構', status: 'SHIPPED', restriction: 'NONE', claimedAt: '2026-10-01T00:00:00Z', canRelease: false },
  ],
}

vi.mock('../../lib/api', async (original) => ({
  ...(await original<object>()),
  api: {
    get: vi.fn(async (path: string) => path.startsWith('/api/admin/users/') ? detail : { content: [user], totalElements: 1 }),
    post: vi.fn(async () => ({})),
  },
}))
vi.mock('../../components/ui/toastContext', () => ({ useToast: () => ({ show: vi.fn() }) }))

function renderPage() {
  render(<QueryClientProvider client={new QueryClient()}><AdminUsers /></QueryClientProvider>)
}

describe('AdminUsers（認領者違規處置）', () => {
  beforeEach(() => vi.mocked(api.post).mockClear())

  it('列出認領者；開抽屜載入個資後，只有還沒寄出的認領能釋出', async () => {
    const userEv = userEvent.setup()
    renderPage()
    await userEv.click(await screen.findByText('donor@example.com'))
    await userEv.click(await screen.findByRole('button', { name: '載入' }))

    expect(await screen.findByText('王大明')).toBeInTheDocument()
    expect(screen.getByText('還沒寄的')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: '釋出這筆認領' })).toHaveLength(1)
    expect(screen.getByText(/已寄出，不能釋出/)).toBeInTheDocument()
  })

  it('停權：填寫原因後打對的端點', async () => {
    const userEv = userEvent.setup()
    renderPage()
    await userEv.click(await screen.findByText('donor@example.com'))
    await userEv.click(await screen.findByRole('button', { name: '停權這位認領者' }))

    const heading = await screen.findByText(/停權「違規的民眾」？/, { selector: 'h2' })
    const dialog = within(heading.closest('dialog') as HTMLElement)
    await userEv.type(dialog.getByRole('textbox', { hidden: true }), '對機構騷擾')
    await userEv.click(dialog.getByRole('button', { name: '停權', hidden: true }))

    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/admin/users/u1/suspend', { note: '對機構騷擾' }))
  })
})
