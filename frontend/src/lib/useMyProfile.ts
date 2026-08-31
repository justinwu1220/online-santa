import { useQuery } from '@tanstack/react-query'
import { api } from './api'
import { useAuth } from './authContext'
import type { UserProfile } from './types'

/** 個人檔案設定頁的資料，見 GET /api/me/profile。 */
export function useMyProfile() {
  const { email } = useAuth()
  return useQuery({
    queryKey: ['me', 'profile', email],
    queryFn: () => api.get<UserProfile>('/api/me/profile'),
    enabled: Boolean(email),
  })
}
