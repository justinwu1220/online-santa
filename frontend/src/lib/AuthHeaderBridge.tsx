import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { registerAuthHeaderProvider, useAuth } from './authContext'

/**
 * 把 AuthProvider 的取標頭函式登記給 api 模組。
 *
 * fetch wrapper 不是 React 元件，拿不到 context，因此需要這個橋接。
 * 順帶在身分變動時清掉查詢快取——換人登入後不該還看得到上一個人的資料。
 */
export function AuthHeaderBridge() {
  const { email, authHeaders } = useAuth()
  const queryClient = useQueryClient()

  useEffect(() => {
    registerAuthHeaderProvider(authHeaders)
  }, [authHeaders])

  useEffect(() => {
    void queryClient.clear()
  }, [email, queryClient])

  return null
}
