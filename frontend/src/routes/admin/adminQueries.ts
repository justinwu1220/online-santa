import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api, withQuery } from '../../lib/api'
import type {
  AdminWishCounts, AdminWishDetail, AdminWishFilter, AdminWishListItem,
  OrganizationReviewView, PageResponse, PlatformStats,
} from '../../lib/types'

/**
 * 管理員後台的資料來源。機構審核與願望動作成功後一律 {@link useInvalidateAdmin}，
 * 清單、抽屜、篩選數量、總覽統計與選單上的待審核數就一起更新。
 */
export function usePlatformStats() {
  return useQuery({
    queryKey: ['admin-stats'],
    queryFn: () => api.get<PlatformStats>('/api/admin/stats'),
    staleTime: 30_000,
  })
}

export function useAdminWishCounts() {
  return useQuery({
    queryKey: ['admin-wish-counts'],
    queryFn: () => api.get<AdminWishCounts>('/api/admin/wishes/counts'),
    staleTime: 30_000,
  })
}

export function useAdminWishes(filter: AdminWishFilter, q: string, year?: number) {
  return useQuery({
    queryKey: ['admin-wishes', filter, q, year ?? 'all'],
    queryFn: () => api.get<PageResponse<AdminWishListItem>>(
      withQuery('/api/admin/wishes', { filter, q: q || undefined, year, size: 100 })),
  })
}

export function useAdminWish(id: string | null) {
  return useQuery({
    queryKey: ['admin-wish', id],
    queryFn: () => api.get<AdminWishDetail>(`/api/admin/wishes/${id}`),
    enabled: id !== null,
  })
}

/** 機構一次取 200 筆（機構數有限），排序與篩選在前端。 */
export function useAdminOrganizations() {
  return useQuery({
    queryKey: ['admin-organizations'],
    queryFn: () => api.get<PageResponse<OrganizationReviewView>>(
      withQuery('/api/admin/organizations', { size: 200 })),
  })
}

/** 選單上團隊對話的紅色數字。 */
export function useAdminTeamUnread() {
  return useQuery({
    queryKey: ['admin-team-unread'],
    queryFn: () => api.get<{ unread: number }>('/api/admin/team-chat/unread'),
    refetchInterval: 60_000,
  })
}

export function useInvalidateAdmin() {
  const queryClient = useQueryClient()
  return async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['admin-wishes'] }),
      queryClient.invalidateQueries({ queryKey: ['admin-wish'] }),
      queryClient.invalidateQueries({ queryKey: ['admin-wish-counts'] }),
      queryClient.invalidateQueries({ queryKey: ['admin-organizations'] }),
      queryClient.invalidateQueries({ queryKey: ['admin-stats'] }),
      queryClient.invalidateQueries({ queryKey: ['audit-logs'] }),
      queryClient.invalidateQueries({ queryKey: ['admin-team-chat'] }),
      queryClient.invalidateQueries({ queryKey: ['admin-team-unread'] }),
    ])
  }
}
