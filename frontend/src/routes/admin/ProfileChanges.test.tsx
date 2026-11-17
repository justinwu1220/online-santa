// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { api } from '../../lib/api'
import type { PageResponse, ProfileChangeView } from '../../lib/types'
import { ProfileChangeBox, ProfileChangeRows } from './ProfileChanges'

const rows: ProfileChangeView[] = [
  {
    id: 2,
    targetType: 'USER',
    targetId: 'u1',
    targetLabel: 'donor@example.com',
    changes: [
      { field: 'phone', before: '0912-000-000', after: '0912-345-678' },
      { field: 'name', before: '', after: '王小明' },
    ],
    actorUserId: 'u1',
    actorEmail: 'donor@example.com',
    actorRole: 'DONOR',
    source: 'SELF_SERVICE',
    occurredAt: '2026-11-01T02:00:00Z',
  },
  {
    id: 1,
    targetType: 'USER',
    targetId: 'u1',
    targetLabel: 'donor@example.com',
    changes: [{ field: 'displayName', before: 'donor@example.com', after: '小明' }],
    actorUserId: 'u1',
    actorEmail: 'donor@example.com',
    actorRole: 'DONOR',
    source: 'SYSTEM',
    occurredAt: '2026-10-01T02:00:00Z',
  },
]

const page: PageResponse<ProfileChangeView> = {
  content: rows, page: 0, size: 30, totalElements: 2, totalPages: 1, hasNext: false,
}

vi.mock('../../lib/api', async (original) => ({
  ...(await original<object>()),
  api: { get: vi.fn(async () => page) },
}))

describe('個資異動紀錄的呈現', () => {
  it('欄位顯示中文標籤，舊值與新值分開呈現', () => {
    render(<ProfileChangeRows rows={rows} />)

    const phone = screen.getByText('聯絡電話').closest('.wh-change') as HTMLElement
    expect(within(phone).getByText('0912-000-000')).toBeInTheDocument()
    expect(within(phone).getByText('0912-345-678')).toBeInTheDocument()
    // 原本是空的要顯示「（空）」，不是空白
    const name = screen.getByText('真實姓名').closest('.wh-change') as HTMLElement
    expect(within(name).getByText('（空）')).toBeInTheDocument()
  })

  it('區分本人修改與系統自動——後者是使用者沒按任何按鈕就被改掉的', () => {
    render(<ProfileChangeRows rows={rows} />)
    expect(screen.getByText('本人修改')).toBeInTheDocument()
    expect(screen.getByText('系統自動')).toBeInTheDocument()
  })

  it('沒有紀錄時表格顯示說明，不是空白', () => {
    render(<ProfileChangeRows rows={[]} />)
    expect(screen.getByText('還沒有任何個資異動紀錄。')).toBeInTheDocument()
  })

  it('抽屜區塊要點擊才載入，並且說明每次載入都會寫稽核', async () => {
    const user = userEvent.setup()
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ProfileChangeBox targetType="USER" targetId="u1" />
      </QueryClientProvider>,
    )

    // 還沒點之前不打 API——那支每呼叫一次就寫一筆稽核
    expect(api.get).not.toHaveBeenCalled()
    expect(screen.getByText(/載入會另外寫入一筆稽核紀錄/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '載入異動紀錄' }))

    expect(await screen.findByText('聯絡電話')).toBeInTheDocument()
    expect(api.get).toHaveBeenCalledTimes(1)
    expect(vi.mocked(api.get).mock.calls[0][0]).toContain('targetId=u1')
  })
})
