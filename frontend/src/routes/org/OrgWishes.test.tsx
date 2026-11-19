// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Outlet, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { OrganizationView, WishListing } from '../../lib/types'
import { OrgWishes } from './OrgWishes'

const get = vi.fn()

vi.mock('../../lib/api', async (original) => ({
  ...(await original<object>()),
  api: { get: (path: string) => get(path), post: vi.fn(), put: vi.fn() },
}))

const organization = { id: 'o1', name: '快樂之家', canDraftWishes: true, canPublishWishes: true } as OrganizationView

const counts = { all: 0, draft: 0, pendingReview: 0, open: 0, claimed: 0, done: 0, claimedWaiting: 0, claimedOverdue: 0, shipped: 0 }

function mockApi(listing: WishListing | Error) {
  get.mockImplementation(async (path: string) => {
    if (path === '/api/organizations/me/wish-listing') {
      if (listing instanceof Error) throw listing
      return listing
    }
    if (path.endsWith('/stage-counts')) return counts
    if (path.startsWith('/api/organizations/me/wishes')) {
      return { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0, hasNext: false }
    }
    if (path === '/api/wishes/options') return { ageRanges: [], categories: [], priceRanges: [], interests: [] }
    return {}
  })
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/org']}>
        <Routes>
          <Route element={<Outlet context={{ organization }} />}>
            <Route path="/org" element={<OrgWishes />} />
          </Route>
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => { get.mockReset() })

describe('願望管理：新增願望只能在上架期間內', () => {
  it('開放中：按鈕可用，沒有說明', async () => {
    mockApi({ open: true, windowStart: '2026-11-25', windowEnd: '2027-01-10' })
    renderPage()

    const button = await screen.findByRole('button', { name: '新增願望' })
    expect(button.hasAttribute('disabled')).toBe(false)
    expect(screen.queryByText(/目前不在願望上架期間/)).toBeNull()
  })

  it('不在上架期間：停用按鈕，說明下一次上架期間，既有願望仍可修改', async () => {
    mockApi({ open: false, windowStart: '2026-11-25', windowEnd: '2027-01-10' })
    renderPage()

    expect(await screen.findByText(/目前不在願望上架期間，無法新增願望（既有願望仍可修改與提交審核）/)).toBeTruthy()
    expect(screen.getByText(/下一次上架期間：2026年11月25日 至 2027年1月10日/)).toBeTruthy()
    expect((screen.getByRole('button', { name: '新增願望' }) as HTMLButtonElement).disabled).toBe(true)
  })

  it('沒有進行中的檔期：停用按鈕並說明由管理員設定', async () => {
    mockApi({ open: false })
    renderPage()

    expect(await screen.findByText(/目前沒有進行中的檔期，檔期由管理員設定/)).toBeTruthy()
    expect((screen.getByRole('button', { name: '新增願望' }) as HTMLButtonElement).disabled).toBe(true)
  })

  it('查詢失敗：不擋按鈕（後端才是最後一道關卡）', async () => {
    mockApi(new Error('boom'))
    renderPage()

    const button = await screen.findByRole('button', { name: '新增願望' })
    expect((button as HTMLButtonElement).disabled).toBe(false)
  })
})
