// @vitest-environment jsdom
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../../lib/api'
import type { SuspensionDetail } from '../../lib/types'
import { SuspensionPanel } from './SuspensionPanel'

const detail: SuspensionDetail = {
  reason: '疑似冒名，先停權調查',
  suspendedAt: '2026-10-22T02:00:00Z',
  snapshot: { name: '停權測試機構', contactPerson: '王承辦', documents: [{ id: 'd1', fileName: '立案證書.pdf' }] },
  revisions: [{
    changes: [
      { field: 'contactPerson', before: '王承辦', after: '新的承辦人' },
      { field: 'withholdingTaxId', before: '', after: '12345678' },
    ],
    at: '2026-10-22T03:00:00Z',
  }],
  unlockRequest: { id: 1, status: 'PENDING', message: '資料已補齊', createdAt: '2026-10-22T04:00:00Z' },
  openWishes: 3,
  affectedClaims: 2,
}

vi.mock('../../lib/api', async (original) => ({
  ...(await original<object>()),
  api: { get: vi.fn(async () => detail), post: vi.fn(async () => ({ notified: 2 })) },
}))
vi.mock('./adminQueries', () => ({ useInvalidateAdmin: () => async () => {} }))
vi.mock('../../components/ui/toastContext', () => ({ useToast: () => ({ show: vi.fn() }) }))

describe('SuspensionPanel（停權機構的處置資料）', () => {
  beforeEach(() => vi.mocked(api.post).mockClear())

  it('點擊載入：顯示原因、當下的原始資訊、停權期間的修改與解除申請', async () => {
    const user = userEvent.setup()
    render(<SuspensionPanel orgId="org-1" orgName="停權測試機構" />)
    await user.click(screen.getByRole('button', { name: '載入處置資料' }))

    expect(await screen.findByText('疑似冒名，先停權調查')).toBeInTheDocument()
    expect(screen.getByText(/影響 3 個公開中的願望、2 筆進行中的認領/)).toBeInTheDocument()
    expect(screen.getByText(/立案證書\.pdf/)).toBeInTheDocument()
    // 停權期間的修改：舊值與新值分屬兩個元素（.wh-change 的刪除線／粗體樣式），所以分開斷言。
    // 要限定在這張卡片裡——「承辦人」在上面那張「停權當下的原始資訊」也會出現
    const revisions = within(screen.getByText(/停權期間的修改/).closest('.dcard') as HTMLElement)
    const changed = revisions.getByText('承辦人').closest('.wh-change') as HTMLElement
    expect(within(changed).getByText('王承辦')).toBeInTheDocument()
    expect(within(changed).getByText('新的承辦人')).toBeInTheDocument()
    // 欄位顯示中文標籤，不是欄位代號
    expect(screen.getByText(/扣繳單位統一編號/)).toBeInTheDocument()
    expect(screen.getByText('資料已補齊')).toBeInTheDocument()
  })

  it('通知受影響的認領者：填寫內容後打對的端點', async () => {
    const user = userEvent.setup()
    render(<SuspensionPanel orgId="org-1" orgName="停權測試機構" />)
    await user.click(screen.getByRole('button', { name: '載入處置資料' }))
    await user.click(await screen.findByRole('button', { name: '通知受影響的認領者' }))

    const heading = await screen.findByText('通知受影響的認領者？', { selector: 'h2' })
    const dialog = within(heading.closest('dialog') as HTMLElement)
    await user.type(dialog.getByRole('textbox', { hidden: true }), '該機構疑似冒名，請先不要再寄任何東西')
    await user.click(dialog.getByRole('button', { name: '寄出通知', hidden: true }))

    await waitFor(() => expect(api.post).toHaveBeenCalledWith(
      '/api/admin/organizations/org-1/notify-donors', { note: '該機構疑似冒名，請先不要再寄任何東西' }))
  })
})
