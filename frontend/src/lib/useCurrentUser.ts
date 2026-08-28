import { useQuery } from '@tanstack/react-query'
import { api } from './api'
import { useAuth } from './authContext'
import type { CurrentUser, UserRole } from './types'

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

/**
 * 這個身分實際生效的角色。
 *
 * 必須與後端的 `AppPrincipal.effectiveRole()` 一致：信箱未驗證時只有一般民眾的
 * 權限。前端若改看資料庫的 `role`，未驗證的管理員會被放進監控中心，然後每一個
 * API 都回 403——畫面進得去、什麼都做不了，比直接擋下來更難理解。
 */
export function effectiveRoleOf(user?: CurrentUser): UserRole | undefined {
  if (!user) return undefined
  return user.emailVerified ? user.role : 'DONOR'
}
