// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { ClaimDonorView, DonorReceiptView } from '../../lib/types'
import { DeadlineBar, ReceiptProofPanel, ShipSummary } from './ClaimParts'

const claim = {
  status: 'SHIPPED',
  trackingCarrier: '郵局',
  trackingNumber: 'R1',
  receiptRequested: true,
} as ClaimDonorView

const receipt: DonorReceiptView = {
  name: '王大明',
  maskedIdNumber: 'A1******89',
  email: 'receipt@example.com',
  address: '台北市中正區某路 1 號',
  proofs: [{
    id: 'p1', purpose: 'RECEIPT_PROOF', url: 'https://example.com/invoice.pdf',
    contentType: 'application/pdf', uploadedAt: '2026-10-01T00:00:00Z', fileName: 'invoice.pdf',
  }],
}

describe('ShipSummary 的捐贈收據', () => {
  it('有收據資料時列出自己填的內容，證號是遮罩後的', () => {
    render(<ShipSummary claim={claim} proofs={[]} receipt={receipt} />)
    expect(screen.getByText('王大明')).toBeInTheDocument()
    expect(screen.getByText('A1******89')).toBeInTheDocument()
    expect(screen.getByText('receipt@example.com')).toBeInTheDocument()
    expect(screen.getByText('台北市中正區某路 1 號')).toBeInTheDocument()
    expect(screen.getByText(/1 個檔案/)).toBeInTheDocument()
  })

  it('發票證明是 PDF 時，查看視窗裡以連結開啟', () => {
    render(<ShipSummary claim={claim} proofs={[]} receipt={receipt} />)
    // 寄件照片 0 張沒有「查看」，唯一的「查看」是發票證明
    fireEvent.click(screen.getByRole('button', { name: '查看' }))
    expect(screen.getByRole('link', { name: 'invoice.pdf' })).toHaveAttribute('href', receipt.proofs[0].url)
  })

  it('寄件照片點開是彈窗放大，不是跳新分頁', () => {
    const photo = {
      id: 's1', purpose: 'SHIPPING_PROOF' as const, url: 'https://example.com/ship.jpg',
      contentType: 'image/jpeg', uploadedAt: '2026-10-01T00:00:00Z',
    }
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ShipSummary claim={claim} proofs={[photo]} />
      </QueryClientProvider>,
    )
    fireEvent.click(screen.getByRole('button', { name: '查看' }))
    expect(document.querySelector('.proof a[target="_blank"]')).toBeNull()

    fireEvent.click(screen.getByRole('button', { name: '放大檢視' }))
    expect(screen.getByText(/放大檢視：照片 1/)).toBeInTheDocument()
  })

  it('機構確認收到：先顯示前 3 張，其餘收進 +N，點了才跳出彈窗', () => {
    const photos = [1, 2, 3, 4, 5].map((n) => ({
      id: `f${n}`, purpose: 'ORG_FEEDBACK' as const, url: `https://example.com/${n}.jpg`,
      contentType: 'image/jpeg', uploadedAt: '2026-10-01T00:00:00Z',
    }))
    render(
      <QueryClientProvider client={new QueryClient()}>
        <ReceiptProofPanel claim={{ ...claim, status: 'RECEIVED', receivedOn: '2026-10-10' }} proofs={photos} />
      </QueryClientProvider>,
    )
    expect(screen.getAllByRole('button', { name: '放大檢視' })).toHaveLength(3)
    fireEvent.click(screen.getByRole('button', { name: '再看其餘 2 張' }))
    expect(screen.getByText('4 / 5')).toBeInTheDocument()
  })

  it('還沒載入收據資料（或沒申請）時不列出收據欄位', () => {
    render(<ShipSummary claim={claim} proofs={[]} />)
    expect(screen.getByText('需要（已提供開據資料）')).toBeInTheDocument()
    expect(screen.queryByText('收據抬頭')).toBeNull()
  })
})

describe('DeadlineBar 的暫停處理中', () => {
  it('願望被凍結且還沒寄出：顯示暫停處理中，不再顯示寄出期限', () => {
    render(<DeadlineBar claim={{ ...claim, status: 'CLAIMED', paused: true }} remaining={-3} />)
    expect(screen.getByText('暫停處理中：暫時不用寄出禮物')).toBeInTheDocument()
    expect(screen.queryByText(/已逾期/)).toBeNull()
  })

  it('沒有凍結：照常顯示寄出期限', () => {
    render(<DeadlineBar claim={{ ...claim, status: 'CLAIMED', paused: false, shipDeadlineAt: '2026-12-01T00:00:00Z' }} remaining={5} />)
    expect(screen.getByText(/前寄出禮物/)).toBeInTheDocument()
    expect(screen.queryByText(/暫停處理中/)).toBeNull()
  })
})
