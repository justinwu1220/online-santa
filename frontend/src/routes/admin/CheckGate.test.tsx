// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { useCheckGate } from './CheckGate'

/**
 * 收據檢查彈窗：摘要、問題清單，以及下面那份每張收據一列的明細名單
 * （名單的問題標記是用收據編號跟檢查結果對起來的）。
 */

const check = {
  campaignId: 'camp-1',
  campaignName: '測試檔期',
  checkedAt: '2026-11-25T00:00:00Z',
  completedWishes: 3,
  validReceipts: 2,
  voidedReceipts: 1,
  totalValue: 600,
  pdfReady: 2,
  pdfPending: 0,
  pdfFailed: 0,
  issues: [
    { severity: 'WARNING', code: 'NO_EVIDENCE', receiptNo: 'OS2026-000001', message: '沒有上傳發票佐證（時價 300 元）' },
    { severity: 'ERROR', code: 'MISSING_ID_NUMBER', receiptNo: 'OS2026-000002', message: '捐贈者要求收據，但沒有證號資料' },
    { severity: 'ERROR', code: 'MISSING_RECEIPT', receiptNo: null, message: '已完成的願望「拼圖」（認領 3f9a2b1c）沒有有效收據：缺少禮物資料（品項、數量或金額），無法開立' },
  ],
}

function receipt(no: string, extra: Record<string, unknown> = {}) {
  return {
    id: 'r-' + no, claimId: 'c-' + no, receiptNo: no, status: 'ISSUED', pdfStatus: 'READY',
    donorName: '善心人士', receiptRequested: false, giftDescription: '玩具小汽車', giftQuantity: 1,
    giftUnit: '份', giftValue: 300, donatedOn: '2026-11-24', issuedOn: '2026-11-24',
    organizationName: '快樂兒童之家', ...extra,
  }
}

const roster = {
  content: [
    receipt('OS2026-000001'),
    receipt('OS2026-000002', { donorName: '王測試', receiptRequested: true, donorEmail: 'a@test.local' }),
    receipt('OS2026-000003', { status: 'VOIDED', voidReason: '姓名填錯' }),
  ],
  page: 0, size: 500, totalElements: 3, totalPages: 1,
}

vi.mock('../../lib/api', async (original) => ({
  ...(await original<object>()),
  api: {
    get: vi.fn(async (path: string) => (path.includes('receipt-check') ? check : roster)),
    post: vi.fn(), put: vi.fn(),
  },
}))

function Harness() {
  const gate = useCheckGate('camp-1')
  return <><button type="button" onClick={gate.view}>檢視收據檢查</button>{gate.dialog}</>
}

async function openDialog() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><Harness /></QueryClientProvider>)
  await userEvent.click(screen.getByRole('button', { name: '檢視收據檢查' }))
  await waitFor(() => expect(screen.getByText(/收據名單/)).toBeInTheDocument())
}

function row(receiptNo: string) {
  return screen.getByRole('cell', { name: receiptNo }).closest('tr') as HTMLElement
}

describe('收據檢查彈窗', () => {
  it('摘要與問題清單照舊，沒有收據編號的問題（願望沒開立收據）留在問題清單', async () => {
    await openDialog()

    expect(screen.getByText(/收據檢查：有 2 項需處理/)).toBeInTheDocument()
    expect(screen.getByText(/沒有有效收據：缺少禮物資料/)).toBeInTheDocument()
  })

  it('名單每張收據一列，並標出 PDF、紅聯與這張的問題', async () => {
    await openDialog()

    expect(screen.getByText('收據名單（3 張，含作廢）')).toBeInTheDocument()

    // 沒有發票佐證（提醒）
    expect(within(row('OS2026-000001')).getByText('無發票佐證')).toBeInTheDocument()
    expect(within(row('OS2026-000001')).getByText('PDF 已產生')).toBeInTheDocument()

    // 要收據、紅聯還沒寄、缺證號（需處理）
    const second = within(row('OS2026-000002'))
    expect(second.getByText('要收據')).toBeInTheDocument()
    expect(second.getByText('紅聯待寄')).toBeInTheDocument()
    expect(second.getByText('缺證號')).toBeInTheDocument()

    // 作廢的只標已作廢，不標 PDF 與紅聯
    const third = within(row('OS2026-000003'))
    expect(third.getByText('已作廢')).toBeInTheDocument()
    expect(third.queryByText('PDF 已產生')).toBeNull()
  })
})
