// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import type { ClaimDonorView } from '../lib/types'
import { ClaimDetail } from './ClaimDetail'

const claim = {
  id: 'c1', wishId: 'w1', status: 'CLAIMED', childAlias: '小星', wishTitle: '色鉛筆組',
  organizationName: '陽光之家', organizationAddress: '台北市某路 1 號', receiptRequested: false,
  shipDeadlineAt: '2099-01-01T00:00:00Z', releasePolicySnapshot: 'NONE',
} as unknown as ClaimDonorView

vi.mock('../lib/api', async (original) => ({
  ...(await original<object>()),
  api: {
    get: vi.fn(async (path: string) => {
      if (path === '/api/claims/c1') return claim
      if (path.endsWith('/attachments')) return []
      if (path === '/api/claims/c1/wish') return { id: 'w1', title: '色鉛筆組', childAlias: '小星', letterPhotoUrls: [] }
      if (path.endsWith('/messages')) return []
      throw new Error(`unexpected GET ${path}`)
    }),
    post: vi.fn(),
  },
}))
// 登入者自己的顯示名稱：標題不應該出現它
vi.mock('../lib/useMyProfile', () => ({ useMyProfile: () => ({ data: { displayName: '吳大善人' } }) }))

describe('ClaimDetail 的標題', () => {
  it('「謝謝你成為…的聖誕老人」顯示孩子的暱稱，不是認領者自己的名稱', async () => {
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter initialEntries={['/me/claims/c1']}>
          <Routes><Route path="/me/claims/:id" element={<ClaimDetail />} /></Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )
    const heading = await screen.findByText(/謝謝你成為/)
    expect(heading).toHaveTextContent('謝謝你成為 小星 的聖誕老人')
    expect(heading).not.toHaveTextContent('吳大善人')
  })
})
