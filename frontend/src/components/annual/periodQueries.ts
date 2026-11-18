import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api, withQuery } from '../../lib/api'
import type { PeriodList } from '../../lib/types'

/**
 * 檔期營運統計的資料來源，管理員與機構兩頁共用，只差端點前綴。
 * 期間 key 是檔期 id 或 'unassigned'；key 還沒決定（選單載入中）時不查詢。
 */
export type PeriodScope = 'admin' | 'organization'

const BASE: Record<PeriodScope, string> = {
  admin: '/api/admin/stats',
  organization: '/api/organizations/me/stats',
}

export function usePeriodList(scope: PeriodScope) {
  return useQuery({
    queryKey: [scope, 'periods'],
    queryFn: () => api.get<PeriodList>(`${BASE[scope]}/periods`),
  })
}

export function usePeriodStats<T>(scope: PeriodScope, key: string | undefined) {
  return useQuery({
    queryKey: [scope, 'period-stats', key],
    enabled: key !== undefined,
    queryFn: () => api.get<T>(withQuery(`${BASE[scope]}/period`, { period: key })),
    // 切換期間時保留舊資料直到新的回來，避免畫面整個閃掉
    placeholderData: keepPreviousData,
  })
}
