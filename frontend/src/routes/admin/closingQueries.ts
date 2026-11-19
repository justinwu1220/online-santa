import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, withQuery } from '../../lib/api'
import type {
  CampaignRequest, CampaignView, ClosingStage, ExportView, FinanceData, MailProgress, PageResponse, PdfKind, PdfStatus,
  ReceiptCheck, ReceiptDownload, ReceiptStatus, ReceiptView,
} from '../../lib/types'

/** 結案文件的資料來源：活動檔期與收據。動作成功後一律讓檔期與收據清單重新載入。 */
export function useCampaigns() {
  return useQuery({
    queryKey: ['closing-campaigns'],
    queryFn: () => api.get<CampaignView[]>('/api/admin/campaigns'),
  })
}

export interface ReceiptFilter {
  status?: ReceiptStatus
  pdfStatus?: PdfStatus
  q: string
  page: number
}

export function useReceipts(campaignId: string | undefined, filter: ReceiptFilter) {
  return useQuery({
    queryKey: ['closing-receipts', campaignId, filter],
    enabled: campaignId !== undefined,
    queryFn: () => api.get<PageResponse<ReceiptView>>(withQuery('/api/admin/receipts', {
      campaignId, status: filter.status, pdfStatus: filter.pdfStatus, q: filter.q || undefined,
      page: filter.page, size: 20,
    })),
  })
}

export function useFinance(campaignId: string) {
  return useQuery({
    queryKey: ['closing-finance', campaignId],
    queryFn: () => api.get<FinanceData>(`/api/admin/campaigns/${campaignId}/finance`),
  })
}

export function useSaveFinance(campaignId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: FinanceData) => api.put<FinanceData>(`/api/admin/campaigns/${campaignId}/finance`, data),
    onSuccess: (saved) => {
      queryClient.setQueryData(['closing-finance', campaignId], saved)
      void queryClient.invalidateQueries({ queryKey: ['audit-logs'] })
    },
  })
}

/** 收據檢查：按「執行檢查」才查（要掃整個檔期），不自動重抓，避免看到半新半舊的結果。 */
export function useReceiptCheck(campaignId: string, enabled: boolean) {
  return useQuery({
    queryKey: ['closing-receipt-check', campaignId],
    enabled,
    staleTime: Infinity,
    gcTime: 0,
    queryFn: () => api.get<ReceiptCheck>(`/api/admin/campaigns/${campaignId}/receipt-check`),
  })
}

function useRefreshClosing() {
  const queryClient = useQueryClient()
  return () => {
    void queryClient.invalidateQueries({ queryKey: ['closing-campaigns'] })
    // 檔期的勸募期間一改，所有營運週期的起訖與歸屬都跟著變
    void queryClient.invalidateQueries({ queryKey: ['admin', 'periods'] })
    void queryClient.invalidateQueries({ queryKey: ['admin', 'period-stats'] })
    void queryClient.invalidateQueries({ queryKey: ['closing-receipts'] })
    void queryClient.invalidateQueries({ queryKey: ['closing-receipt-check'] })
    void queryClient.invalidateQueries({ queryKey: ['audit-logs'] })
  }
}

export function useSaveCampaign() {
  const refresh = useRefreshClosing()
  return useMutation({
    mutationFn: ({ id, request }: { id?: string; request: CampaignRequest }) =>
      id ? api.put<CampaignView>(`/api/admin/campaigns/${id}`, request)
        : api.post<CampaignView>('/api/admin/campaigns', request),
    onSuccess: refresh,
  })
}

export function useVoidReceipt() {
  const refresh = useRefreshClosing()
  return useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      api.post<ReceiptView>(`/api/admin/receipts/${id}/void`, { reason }),
    onSuccess: refresh,
  })
}

/** 觸發瀏覽器下載。網址的 Content-Disposition 是 attachment，所以不會離開目前頁面。 */
function triggerDownload(url: string) {
  const link = document.createElement('a')
  link.href = url
  link.rel = 'noopener'
  document.body.appendChild(link)
  link.click()
  link.remove()
}

/**
 * 全部下載：建立匯出、反覆推進（每次後端在約 15 秒內做一段）直到完成，再下載壓縮檔。
 * 進度透過 onProgress 回報；失敗時丟出錯誤。回傳最後的匯出狀態（含略過張數）。
 */
export function downloadAllReceipts(
  campaignId: string, onProgress: (view: ExportView) => void,
): Promise<ExportView> {
  return runExportJob(`/api/admin/campaigns/${campaignId}/receipt-exports`, onProgress)
}

export type MailKind = 'receipts' | 'org-forms'

function mailPath(campaignId: string, kind: MailKind, stage: ClosingStage) {
  const base = `/api/admin/campaigns/${campaignId}/closing-mail/${kind}`
  return { preview: kind === 'org-forms' ? `${base}?stage=${stage}` : base, send: `${base}/send` }
}

/** 寄送前預覽：只看將寄幾封，不寄信。 */
export function previewMail(campaignId: string, kind: MailKind, stage: ClosingStage) {
  return api.get<MailProgress>(mailPath(campaignId, kind, stage).preview)
}

/**
 * 分段寄出：每次呼叫後端在約 15 秒內寄一段，反覆呼叫到待寄為 0、額度用完（stopped）或這一輪沒有進展為止。
 * 失敗的信不會在同一輪重試（要重試用 retryFailed 另開一輪）。
 */
export async function sendMail(
  campaignId: string, kind: MailKind, stage: ClosingStage, retryFailed: boolean, onProgress: (p: MailProgress) => void,
): Promise<MailProgress> {
  const { send } = mailPath(campaignId, kind, stage)
  let retry = retryFailed
  let last: MailProgress | undefined
  for (;;) {
    const params = [kind === 'org-forms' ? `stage=${stage}` : '', retry ? 'retryFailed=true' : ''].filter(Boolean)
    const p = await api.post<MailProgress>(params.length > 0 ? `${send}?${params.join('&')}` : send)
    onProgress(p)
    retry = false
    const stalled = last !== undefined && p.sent === last.sent && p.failed === last.failed
    if (p.pending === 0 || p.stopped !== null || stalled) return p
    last = p
  }
}

/** 結案文件包（官方 Excel、核章 PDF、成果報告、05 表單、收據、待補資料）：流程與全部下載相同。 */
export function downloadClosingPackage(
  campaignId: string, stage: ClosingStage, onProgress: (view: ExportView) => void,
): Promise<ExportView> {
  return runExportJob(`/api/admin/campaigns/${campaignId}/closing-packages?stage=${stage}`, onProgress)
}

async function runExportJob(createPath: string, onProgress: (view: ExportView) => void): Promise<ExportView> {
  let view = await api.post<ExportView>(createPath)
  onProgress(view)
  while (view.status === 'PREPARING') {
    view = await api.post<ExportView>(`/api/admin/closing-exports/${view.id}/advance`)
    onProgress(view)
  }
  if (view.status !== 'READY') {
    throw new Error(view.error ?? '匯出失敗，請稍後再試')
  }
  const download = await api.get<ReceiptDownload>(`/api/admin/closing-exports/${view.id}/download-url`)
  triggerDownload(download.url)
  return view
}

/**
 * 在新分頁開啟收據 PDF（預覽，可再另存）。網址是短效的，每次取得都會寫稽核。
 *
 * 先在使用者按下按鈕的當下同步開好空白分頁，拿到網址再導過去：非同步請求完成後才呼叫
 * window.open，會被瀏覽器當成彈出視窗擋掉。取得網址失敗就把空白分頁關掉，錯誤丟回給呼叫端顯示。
 */
export async function openReceipt(id: string, kind: PdfKind) {
  const tab = window.open('', '_blank')
  if (tab) tab.opener = null
  try {
    const result = await api.get<ReceiptDownload>(
      withQuery(`/api/admin/receipts/${id}/download-url`, { kind }))
    if (tab) {
      tab.location.href = result.url
    } else {
      // 瀏覽器還是擋了空白分頁：退回在同一個分頁開，至少看得到
      window.location.href = result.url
    }
  } catch (e) {
    tab?.close()
    throw e
  }
}
