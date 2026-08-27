import { useQuery } from '@tanstack/react-query'
import { api } from './api'
import { useAuth } from './authContext'
import type { CurrentUser } from './types'

/**
 * 目前登入者的角色與所屬機構。
 *
 * 首次呼叫會讓後端就地建立本地帳號（JIT provisioning）。角色一律以這個端點為準，
 * 而非 Firebase 的 custom claims——後者要等 token 重簽才會更新。
 */
export function useCurrentUser() {
  const { email } = useAuth()
  return useQuery({
    queryKey: ['me', email],
    queryFn: () => api.get<CurrentUser>('/api/me'),
    enabled: Boolean(email),
    staleTime: 60_000,
  })
}
