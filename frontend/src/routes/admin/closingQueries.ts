import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, withQuery } from '../../lib/api'
import type {
  BatchResult, CampaignRequest, CampaignView, PageResponse, PdfKind, PdfStatus, ReceiptDownload,
  ReceiptStatus, ReceiptView,
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

function useRefreshClosing() {
  const queryClient = useQueryClient()
  return () => {
    void queryClient.invalidateQueries({ queryKey: ['closing-campaigns'] })
    void queryClient.invalidateQueries({ queryKey: ['closing-receipts'] })
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

export function useIssueMissingReceipts() {
  const refresh = useRefreshClosing()
  return useMutation({
    mutationFn: (campaignId: string) =>
      api.post<BatchResult>(`/api/admin/campaigns/${campaignId}/issue-missing-receipts`),
    onSuccess: refresh,
  })
}

export function useGenerateReceiptPdfs() {
  const refresh = useRefreshClosing()
  return useMutation({
    mutationFn: (campaignId: string) =>
      api.post<BatchResult>(`/api/admin/campaigns/${campaignId}/generate-receipt-pdfs`),
    onSuccess: refresh,
  })
}

/** 取得短效下載網址（每次都寫稽核），並讓瀏覽器直接下載。 */
export async function downloadReceipt(id: string, kind: PdfKind) {
  const result = await api.get<ReceiptDownload>(
    withQuery(`/api/admin/receipts/${id}/download-url`, { kind }))
  const link = document.createElement('a')
  link.href = result.url
  link.rel = 'noopener'
  document.body.appendChild(link)
  link.click()
  link.remove()
}
