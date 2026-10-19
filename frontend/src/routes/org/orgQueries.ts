import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api, withQuery } from '../../lib/api'
import type {
  OrganizationStageCounts, OrganizationView, PageResponse, WishOrgDetail, WishOrgView, WishStatus,
} from '../../lib/types'

/**
 * 機構後台的資料來源。願望與它目前的認領只有一個查詢源（後端合併讀取端點），
 * 任何動作成功後一律 {@link useInvalidateOrg}，清單、抽屜、篩選數量、未讀數就會一起更新——
 * 這是舊版「願望管理」與「認領管理」兩份清單互不連動的原因被消除的關鍵。
 */
export function useOrganization() {
  return useQuery({
    queryKey: ['organization', 'me'],
    queryFn: () => api.get<OrganizationView>('/api/organizations/me'),
  })
}

export function useStageCounts() {
  return useQuery({
    queryKey: ['org-stage-counts'],
    queryFn: () => api.get<OrganizationStageCounts>('/api/organizations/me/wishes/stage-counts'),
  })
}

/** 側邊選單的紅色數字：所有願望的對話、與團隊的對話。 */
export function useOrgUnread() {
  return useQuery({
    queryKey: ['org-unread'],
    queryFn: () => api.get<{ wishes: number; team: number }>('/api/organizations/me/unread'),
    refetchInterval: 60_000,
  })
}

/** 願望清單。一次取 100 筆（機構的願望數有限），由前端依狀態排序。 */
export function useOrgWishes(status?: WishStatus) {
  return useQuery({
    queryKey: ['org-wishes', status ?? 'all'],
    queryFn: () => api.get<PageResponse<WishOrgView>>(
      withQuery('/api/organizations/me/wishes', { status, size: 100, sort: 'createdAt,desc' })),
  })
}

export function useOrgWish(id: string | null) {
  return useQuery({
    queryKey: ['org-wish', id],
    queryFn: () => api.get<WishOrgDetail>(`/api/organizations/me/wishes/${id}`),
    enabled: Boolean(id),
  })
}

/** 動作成功後呼叫：願望清單、抽屜、篩選數量、未讀數、對話一起重新載入。 */
export function useInvalidateOrg() {
  const queryClient = useQueryClient()
  return () => Promise.all([
    queryClient.invalidateQueries({ queryKey: ['org-wishes'] }),
    queryClient.invalidateQueries({ queryKey: ['org-wish'] }),
    queryClient.invalidateQueries({ queryKey: ['org-stage-counts'] }),
    queryClient.invalidateQueries({ queryKey: ['org-unread'] }),
    queryClient.invalidateQueries({ queryKey: ['org-conversation'] }),
    queryClient.invalidateQueries({ queryKey: ['claim'] }),
  ])
}
