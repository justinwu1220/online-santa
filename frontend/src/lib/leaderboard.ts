import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, withQuery } from './api'
import type { LeaderboardView, MyAchievement, PodiumHistory, UserProfile } from './types'

/**
 * 排行榜的資料來源。
 *
 * 名單對所有人回同一份，而且後端帶了 `Cache-Control: public, max-age=300`——首頁每開一次
 * 就會打一次，CDN 擋得住才不會一直叫醒 scale to zero 的後端。所以這裡的 staleTime 也放寬到
 * 五分鐘，同一個分頁內換頁不會重打。「我的成就」相反：機構確認收到之後要立刻反映，不快取。
 */
const BOARD_STALE_MS = 5 * 60_000

/** 首頁入口只要前三名。切換名字之後要重抓的也是這幾種變體，放在同一個地方才不會漏掉。 */
export const STRIP_LIMIT = 3
const BOARD_VARIANTS: (number | undefined)[] = [undefined, STRIP_LIMIT]

/** 排行榜名單。`limit` 只取前幾名（首頁入口傳 3，不必為了三顆膠囊搬整份名單）。 */
export function useLeaderboard(limit?: number) {
  return useQuery({
    queryKey: ['leaderboard', limit ?? 'all'],
    staleTime: BOARD_STALE_MS,
    queryFn: () => api.get<LeaderboardView>(withQuery('/api/leaderboard', { limit })),
  })
}

/** 我的成就。只有登入者看得到，所以未登入時不要發出請求。 */
export function useMyAchievement(enabled: boolean) {
  return useQuery({
    queryKey: ['leaderboard', 'me'],
    enabled,
    staleTime: 0,
    queryFn: () => api.get<MyAchievement>('/api/me/leaderboard'),
  })
}

/** 歷屆前三名。檔期結案後才會變，放得比名單更久。 */
export function usePodiums() {
  return useQuery({
    queryKey: ['leaderboard', 'podiums'],
    staleTime: 60 * 60_000,
    queryFn: () => api.get<PodiumHistory>('/api/leaderboard/podiums'),
  })
}

/**
 * 「在排行榜顯示我的名字」開關。
 *
 * <strong>名單不能只是 invalidate</strong>：它帶著 `public, max-age=300`，單純重抓會被瀏覽器
 * 用剛才那份（上面還寫著「匿名的聖誕老人」）回答，看起來就像開關沒有作用。這裡用
 * `cache: 'reload'` 強制回源，順便把瀏覽器裡那份換掉，所以重新整理也不會變回舊的。
 * 後端那一份名單快取由 `LeaderboardCacheListener` 在切換時丟掉。
 *
 * 重抓失敗不算切換失敗——設定已經存好了，名單等下次自然更新即可。
 */
export function useSetLeaderboardName() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (optIn: boolean) => api.put<UserProfile>('/api/me/leaderboard-opt-in', { optIn }),
    onSuccess: async () => {
      void queryClient.invalidateQueries({ queryKey: ['leaderboard', 'me'] })
      void queryClient.invalidateQueries({ queryKey: ['me', 'profile'] })
      await Promise.all(BOARD_VARIANTS.map(async (limit) => {
        try {
          const path = withQuery('/api/leaderboard', { limit })
          const fresh = await api.get<LeaderboardView>(path, { cache: 'reload' })
          queryClient.setQueryData(['leaderboard', limit ?? 'all'], fresh)
        } catch {
          queryClient.removeQueries({ queryKey: ['leaderboard', limit ?? 'all'] })
        }
      }))
    },
  })
}
