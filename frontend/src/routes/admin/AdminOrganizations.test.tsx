// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import type { OrganizationReviewView } from '../../lib/types'
import { AdminOrganizations } from './AdminOrganizations'

function org(patch: Partial<OrganizationReviewView> & Pick<OrganizationReviewView, 'id' | 'name' | 'status'>): OrganizationReviewView {
  return {
    contactPerson: '承辦人', contactEmail: 'org@example.com', canDraftWishes: true,
    createdAt: '2026-09-01T00:00:00Z', ...patch,
  }
}

/** 停權中＋有待審解除申請、停權中但沒申請、退件後重新送審（確認兩顆標籤不會互相干擾）。 */
const rows: OrganizationReviewView[] = [
  org({ id: 'o1', name: '有申請的機構', status: 'SUSPENDED', unlockRequested: true, reviewedAt: '2026-10-01T00:00:00Z' }),
  org({ id: 'o2', name: '沒申請的機構', status: 'SUSPENDED', reviewedAt: '2026-10-01T00:00:00Z' }),
  org({ id: 'o3', name: '重送的機構', status: 'PENDING', reviewNote: '請補文件' }),
]

vi.mock('../../lib/api', async (original) => ({
  ...(await original<object>()),
  api: {
    get: vi.fn(async () => ({ content: rows, totalElements: rows.length })),
    post: vi.fn(async () => ({})),
  },
}))
vi.mock('../../components/ui/toastContext', () => ({ useToast: () => ({ show: vi.fn() }) }))

function renderPage(filter: string) {
  render(
    <MemoryRouter initialEntries={[`/admin/organizations?filter=${filter}`]}>
      <QueryClientProvider client={new QueryClient()}><AdminOrganizations /></QueryClientProvider>
    </MemoryRouter>,
  )
}

/** 列出來的那一列（org-row 是 button，標題在 .org-row__title）。 */
async function row(name: string) {
  const title = await screen.findByText(name)
  return title.closest('.org-row') as HTMLElement
}

describe('AdminOrganizations（機構清單的標籤）', () => {
  it('停權中又有待審解除申請：那一列出現「申請解除」，不必開抽屜', async () => {
    renderPage('suspended')
    expect(within(await row('有申請的機構')).getByText('申請解除')).toBeInTheDocument()
  })

  it('停權中但沒有申請：不顯示「申請解除」', async () => {
    renderPage('suspended')
    await row('有申請的機構')
    expect(within(await row('沒申請的機構')).queryByText('申請解除')).toBeNull()
  })

  it('「重新送審」與「申請解除」各歸各的列，不會互相干擾', async () => {
    renderPage('all')
    const resubmitted = within(await row('重送的機構'))
    expect(resubmitted.getByText('重新送審')).toBeInTheDocument()
    expect(resubmitted.queryByText('申請解除')).toBeNull()

    const requested = within(await row('有申請的機構'))
    expect(requested.getByText('申請解除')).toBeInTheDocument()
    expect(requested.queryByText('重新送審')).toBeNull()
  })

  it('整份清單只有那一列掛著「申請解除」', async () => {
    renderPage('all')
    await row('有申請的機構')   // 等資料載完，不然查到的是「載入機構…」
    expect(screen.getAllByText('申請解除')).toHaveLength(1)
  })
})
