import { useQuery, type QueryClient } from '@tanstack/react-query'
import { api } from './api'
import type { CampaignPublic } from './types'

const KEY = ['campaign', 'current']

/** 名單與頁尾一樣吃五分鐘的 HTTP 快取，前端的 staleTime 跟著一致，免得白白重打。 */
const STALE_MS = 5 * 60_000

/**
 * 頁尾要顯示的檔期資訊（目前只有勸募許可文號）。
 *
 * 每一個公開頁都有頁尾，但內容換檔期才會變，所以後端帶五分鐘的 Cache-Control，
 * 這裡的 staleTime 也放五分鐘。抓不到就讓頁尾少一行，不顯示錯誤、也不重試。
 */
export function useCurrentCampaign() {
  return useQuery({
    queryKey: KEY,
    staleTime: STALE_MS,
    retry: false,
    queryFn: () => api.get<CampaignPublic>('/api/campaigns/current'),
  })
}

/**
 * 重抓頁尾的檔期資訊，管理員儲存檔期之後呼叫。
 *
 * <strong>不能只是 invalidate</strong>：回應帶著 `public, max-age=300`，重抓會被瀏覽器用舊的
 * 那份回答，改完文號看起來像沒有生效。`cache: 'reload'` 強制回源，順便把瀏覽器裡那份換掉。
 * 其他人的瀏覽器則是最多五分鐘後跟上。
 */
export async function refreshCurrentCampaign(queryClient: QueryClient) {
  try {
    const fresh = await api.get<CampaignPublic>('/api/campaigns/current', { cache: 'reload' })
    queryClient.setQueryData(KEY, fresh)
  } catch {
    queryClient.removeQueries({ queryKey: KEY })
  }
}
