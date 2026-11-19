// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { CampaignView, PageResponse, PeriodList, PlatformPeriodStats, ReceiptView } from '../../lib/types'
import { AdminAnnualReview } from './AdminAnnualReview'

const campaign: CampaignView = {
  id: 'c1', name: '線上聖誕老人 2026', activityNo: '115000877', permitNo: '衛部救字第1150000號',
  collectionStart: '2026-11-25', collectionEnd: '2027-01-25', useStart: '2026-11-25', useEnd: '2027-01-25',
  wishListStart: '2026-11-25', wishListEnd: '2027-01-10',
  receiptPrefix: 'OS2026', nextReceiptNo: 'OS2026-000003', receiptsIssued: 2, status: 'ACTIVE',
  createdAt: '2026-11-01T00:00:00Z', updatedAt: '2026-11-01T00:00:00Z',
}

const periodList: PeriodList = {
  defaultKey: 'c1',
  periods: [
    { key: 'unassigned', name: '尚未建立檔期', start: '2027-01-26', end: '2027-02-10', unassigned: true },
    { key: 'c1', campaignId: 'c1', name: '線上聖誕老人 2026', start: '2026-01-26', end: '2027-01-25', unassigned: false },
    { key: 'c0', campaignId: 'c0', name: '線上聖誕老人 2025', start: '2025-01-01', end: '2026-01-25', unassigned: false },
  ],
}

const stats: PlatformPeriodStats = {
  period: periodList.periods[1],
  newDonors: 12, newOrganizations: 3, activeDonors: 9, publishedWishes: 40, claimed: 30, completed: 24,
  completionRate: 0.8,
  monthlyClaims: [
    { year: 2026, month: 11, count: 10 }, { year: 2026, month: 12, count: 15 }, { year: 2027, month: 1, count: 5 },
  ],
  claimOutcomes: { COMPLETED: 24, RELEASED: 4, CANCELLED: 2 },
  topOrganizations: [{ organizationId: 'o1', organizationName: '快樂兒童之家', completedCount: 12 }],
}

const receipt = (overrides: Partial<ReceiptView>): ReceiptView => ({
  id: 'r1', claimId: 'k1', receiptNo: 'OS2026-000001', status: 'ISSUED', pdfStatus: 'READY',
  donorName: '王大明', receiptRequested: true, giftDescription: '積木玩具組', giftQuantity: 1, giftUnit: '份',
  giftValue: 1200, donatedOn: '2026-12-25', issuedOn: '2026-12-28', organizationName: '快樂兒童之家',
  ...overrides,
})

const page = (content: ReceiptView[]): PageResponse<ReceiptView> => ({
  content, page: 0, size: 20, totalElements: content.length, totalPages: 1, hasNext: false,
})

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()

vi.mock('../../lib/api', async (original) => ({
  ...(await original<object>()),
  api: {
    get: (path: string) => get(path),
    post: (path: string, body?: unknown) => post(path, body),
    put: (path: string, body?: unknown) => put(path, body),
  },
}))

function renderPage(url = '/admin/annual?tab=receipts') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[url]}><AdminAnnualReview /></MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  get.mockReset()
  post.mockReset()
  put.mockReset()
  get.mockImplementation(async (path: string) => {
    if (path === '/api/admin/stats/periods') return periodList
    if (path.startsWith('/api/admin/stats/period?')) return stats
    if (path.startsWith('/api/admin/campaigns')) return [campaign, { ...campaign, id: 'c0', name: '線上聖誕老人 2025', receiptPrefix: 'OS2025' }]
    if (path.startsWith('/api/admin/receipts?')) {
      return page([
        receipt({}),
        receipt({ id: 'r2', receiptNo: 'OS2026-000002', donorName: '善心人士', receiptRequested: false, pdfStatus: 'PENDING' }),
        receipt({ id: 'r3', receiptNo: 'OS2026-000003', status: 'VOIDED', voidReason: '姓名填錯' }),
      ])
    }
    if (path.includes('/download-url')) return { url: 'https://storage.test/x.pdf', expiresAt: '2026-12-30T00:00:00Z' }
    throw new Error('未預期的請求：' + path)
  })
})

describe('年度營運頁', () => {
  it('顯示檔期資訊與收據編號；缺許可文號時提醒收據不會產生 PDF', async () => {
    renderPage('/admin/annual?tab=campaign')

    expect(await screen.findByText('線上聖誕老人 2026')).toBeTruthy()
    expect(screen.getByText(/已開立 2 張，下一張 OS2026-000003/)).toBeTruthy()
    expect(screen.getByText('衛部救字第1150000號')).toBeTruthy()
    expect(screen.getByText('願望上架期間')).toBeTruthy()
    expect(screen.queryByText('統計截止日')).toBeNull()
  })

  it('收據清單：列出編號、捐贈者與狀態；作廢的收據不能下載或再作廢', async () => {
    renderPage()

    const row1 = (await screen.findByText('OS2026-000001')).closest('tr')!
    expect(within(row1).getByText('要收據')).toBeTruthy()
    expect(within(row1).getByText('已產生')).toBeTruthy()
    const row2 = screen.getByText('OS2026-000002').closest('tr')!
    expect(within(row2).getByText('待產生')).toBeTruthy()
    const row3 = screen.getByText('OS2026-000003').closest('tr')!
    expect(within(row3).getByText('已作廢')).toBeTruthy()
    expect(within(row3).getByRole('button', { name: '預覽白聯' }).hasAttribute('disabled')).toBe(true)
    expect(within(row3).getByRole('button', { name: '預覽黃紅聯' }).hasAttribute('disabled')).toBe(true)
    expect(within(row3).queryByRole('button', { name: /下載/ })).toBeNull()
    expect(within(row3).getByRole('button', { name: '作廢重開' }).hasAttribute('disabled')).toBe(true)
  })

  it('按預覽：在新分頁開啟（先同步開空白分頁，再導向短效網址），不離開收據頁', async () => {
    const tab = { location: { href: '' }, close: vi.fn(), opener: {} }
    const open = vi.spyOn(window, 'open').mockReturnValue(tab as unknown as Window)
    renderPage()
    const row = (await screen.findByText('OS2026-000001')).closest('tr')!

    await userEvent.click(within(row).getByRole('button', { name: '預覽白聯' }))

    await waitFor(() => expect(get).toHaveBeenCalledWith('/api/admin/receipts/r1/download-url?kind=WHITE'))
    expect(open).toHaveBeenCalledWith('', '_blank')
    await waitFor(() => expect(tab.location.href).toBe('https://storage.test/x.pdf'))
    expect(tab.opener).toBeNull()
    expect(tab.close).not.toHaveBeenCalled()
    open.mockRestore()
  })

  it('全部下載：建立匯出、反覆推進到完成、再下載壓縮檔，並說明略過的張數', async () => {
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const base = {
      id: 'e1', campaignId: 'c1', kind: 'RECEIPTS' as const, total: 3, prepared: 0, skipped: 0,
      createdAt: '2026-12-30T00:00:00Z',
    }
    post.mockImplementation(async (path: string) => {
      if (path.endsWith('/receipt-exports')) return { ...base, status: 'PREPARING' }
      if (post.mock.calls.filter(([p]) => String(p).includes('/advance')).length < 2) {
        return { ...base, status: 'PREPARING', prepared: 1 }
      }
      return { ...base, status: 'READY', prepared: 3, skipped: 1 }
    })
    get.mockImplementation(async (path: string) => {
      if (path === '/api/admin/stats/periods') return periodList
      if (path.startsWith('/api/admin/stats/period?')) return stats
      if (path.startsWith('/api/admin/campaigns')) return [campaign]
      if (path.includes('/closing-exports/e1/download-url')) return { url: 'https://storage.test/all.zip', expiresAt: '2026-12-30T01:00:00Z' }
      return page([receipt({})])
    })
    renderPage()
    await screen.findByText('OS2026-000001')

    await userEvent.click(screen.getByRole('button', { name: '全部下載' }))

    expect(await screen.findByText(/已下載壓縮檔（2 張收據）；另有 1 張因 PDF 無法產生/)).toBeTruthy()
    expect(post).toHaveBeenCalledWith('/api/admin/campaigns/c1/receipt-exports', undefined)
    expect(post.mock.calls.filter(([p]) => String(p).includes('/closing-exports/e1/advance')).length).toBe(2)
    expect(click).toHaveBeenCalled()
    click.mockRestore()
  })

  it('全部下載失敗：顯示原因', async () => {
    post.mockRejectedValue(new Error('這個檔期還沒有有效的收據，沒有可匯出的內容'))
    renderPage()
    await screen.findByText('OS2026-000001')

    await userEvent.click(screen.getByRole('button', { name: '全部下載' }))

    expect(await screen.findByText(/還沒有有效的收據/)).toBeTruthy()
  })

  it('取不到網址（例如缺許可文號）：關掉空白分頁並在頁面顯示原因', async () => {
    const tab = { location: { href: '' }, close: vi.fn(), opener: {} }
    const open = vi.spyOn(window, 'open').mockReturnValue(tab as unknown as Window)
    get.mockImplementation(async (path: string) => {
      if (path.includes('/download-url')) throw new Error('檔期尚未填寫勸募許可文號，補上後才能產生收據 PDF')
      if (path === '/api/admin/stats/periods') return periodList
      if (path.startsWith('/api/admin/stats/period?')) return stats
      if (path.startsWith('/api/admin/campaigns')) return [campaign]
      return page([receipt({})])
    })
    renderPage()
    const row = (await screen.findByText('OS2026-000001')).closest('tr')!

    await userEvent.click(within(row).getByRole('button', { name: '預覽白聯' }))

    expect(await screen.findByText(/檔期尚未填寫勸募許可文號/)).toBeTruthy()
    expect(tab.close).toHaveBeenCalled()
    expect(tab.location.href).toBe('')
    open.mockRestore()
  })

  it('作廢重開：原因必填，送出後呼叫後端並顯示新編號', async () => {
    post.mockResolvedValue(receipt({ id: 'r9', receiptNo: 'OS2026-000004' }))
    renderPage()
    const row = (await screen.findByText('OS2026-000001')).closest('tr')!

    await userEvent.click(within(row).getByRole('button', { name: '作廢重開' }))
    await userEvent.click(screen.getByRole('button', { name: '作廢並重開' }))
    expect(screen.getByText('請至少寫 2 個字')).toBeTruthy()
    expect(post).not.toHaveBeenCalled()

    await userEvent.type(screen.getByLabelText(/作廢原因/), '捐贈者姓名填錯')
    await userEvent.click(screen.getByRole('button', { name: '作廢並重開' }))

    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/admin/receipts/r1/void', { reason: '捐贈者姓名填錯' }))
    expect(await screen.findByText(/已作廢 OS2026-000001，重開為 OS2026-000004/)).toBeTruthy()
  })

  it('沒有「補開收據」與「補產 PDF」：收據開立後自動產生 PDF', async () => {
    renderPage()
    await screen.findByText('OS2026-000001')

    expect(screen.queryByRole('button', { name: '補開收據' })).toBeNull()
    expect(screen.queryByRole('button', { name: '補產 PDF' })).toBeNull()
    expect(screen.queryByRole('tab', { name: '待補許可文號' })).toBeNull()
  })

  it('收據檢查：按下才執行，列出需處理與提醒的項目', async () => {
    const result = {
      campaignId: 'c1', campaignName: '線上聖誕老人 2026', checkedAt: '2026-12-30T00:00:00Z',
      completedWishes: 3, validReceipts: 2, voidedReceipts: 1, totalValue: 3600,
      pdfReady: 1, pdfPending: 0, pdfFailed: 0,
      issues: [
        { severity: 'ERROR', code: 'MISSING_RECEIPT', message: '已完成的願望「積木」沒有有效收據：開立失敗，請聯絡系統管理員檢查' },
        { severity: 'WARNING', code: 'NO_EVIDENCE', receiptNo: 'OS2026-000002', message: '沒有上傳發票佐證（時價 1200 元）' },
      ],
    }
    const base = get.getMockImplementation()!
    get.mockImplementation(async (path: string) =>
      path.endsWith('/receipt-check') ? result : base(path))
    renderPage()
    await screen.findByText('OS2026-000001')
    expect(get).not.toHaveBeenCalledWith('/api/admin/campaigns/c1/receipt-check')

    await userEvent.click(screen.getByRole('button', { name: '收據檢查' }))

    expect(await screen.findByText('收據檢查：有 1 項需處理')).toBeTruthy()
    expect(screen.getByText(/時價合計 3,600 元/)).toBeTruthy()
    expect(screen.getByText(/已完成的願望「積木」沒有有效收據/)).toBeTruthy()
    expect(screen.getByText('OS2026-000002：')).toBeTruthy()
  })

  it('預設顯示涵蓋今天的檔期的營運統計，期間選單列出各檔期與「尚未建立檔期」', async () => {
    renderPage('/admin/annual')

    expect(await screen.findByText('完成率 80%')).toBeTruthy()
    expect(screen.getByText('30')).toBeTruthy()
    expect(screen.getByText('完成率 80%')).toBeTruthy()
    const picker = screen.getByLabelText('檔期') as HTMLSelectElement
    expect(picker.value).toBe('c1')
    expect(within(picker).getAllByRole('option').map((o) => o.textContent)).toEqual([
      '尚未建立檔期（2027/1/26 起）',
      '線上聖誕老人 2026（2026/1/26 – 2027/1/25）',
      '線上聖誕老人 2025（2025/1/1 – 2026/1/25）',
    ])
    // 跨年檔期的月份用「年/月」標示，不會把兩年的同月份疊在一起
    await waitFor(() => expect(get).toHaveBeenCalledWith('/api/admin/stats/period?period=c1'))
    expect(screen.getByRole('tab', { name: '檔期設定' })).toBeTruthy()
    expect(screen.getByRole('tab', { name: '收據' })).toBeTruthy()
  })

  it('切換期間：重新查詢該檔期的統計', async () => {
    renderPage('/admin/annual')
    await screen.findByText('完成率 80%')

    await userEvent.selectOptions(screen.getByLabelText('檔期'), 'c0')

    await waitFor(() => expect(get).toHaveBeenCalledWith('/api/admin/stats/period?period=c0'))
  })

  it('選到「尚未建立檔期」：只有營運統計，沒有檔期設定與收據分頁，並提示建立檔期', async () => {
    renderPage('/admin/annual')
    await screen.findByText('完成率 80%')

    await userEvent.selectOptions(screen.getByLabelText('檔期'), 'unassigned')

    expect(await screen.findByText(/還沒有活動檔期/)).toBeTruthy()
    expect(screen.queryByRole('tab', { name: '檔期設定' })).toBeNull()
    expect(screen.queryByRole('tab', { name: '收據' })).toBeNull()
  })

  it('分頁：從營運統計切到檔期設定與收據', async () => {
    renderPage('/admin/annual')
    await screen.findByText('完成率 80%')

    await userEvent.click(screen.getByRole('tab', { name: '檔期設定' }))
    expect(await screen.findByText(/已開立 2 張，下一張 OS2026-000003/)).toBeTruthy()

    await userEvent.click(screen.getByRole('tab', { name: '收據' }))
    expect(await screen.findByText('OS2026-000001')).toBeTruthy()
  })

  it('沒有任何檔期時只有「尚未建立檔期」，仍能新增檔期', async () => {
    get.mockImplementation(async (path: string) => {
      if (path === '/api/admin/stats/periods') {
        return { defaultKey: 'unassigned', periods: [periodList.periods[0]] }
      }
      if (path.startsWith('/api/admin/stats/period?')) return { ...stats, period: periodList.periods[0] }
      if (path.startsWith('/api/admin/campaigns')) return []
      throw new Error('未預期的請求：' + path)
    })
    renderPage('/admin/annual')

    expect(await screen.findByText(/還沒有活動檔期/)).toBeTruthy()
    expect(screen.getByRole('button', { name: '新增活動檔期' })).toBeTruthy()
  })
})
