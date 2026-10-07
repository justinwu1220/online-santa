import { useEffect, useRef } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { registerAuthHeaderProvider, useAuth } from './authContext'

/**
 * 把 AuthProvider 的取標頭函式登記給 api 模組。
 *
 * fetch wrapper 不是 React 元件，拿不到 context，因此需要這個橋接。
 * 順帶在身分變動時清掉查詢快取——換人登入後不該還看得到上一個人的資料。
 *
 * <strong>只在「身分真的變了」才清</strong>：Firebase 模式下 `email` 從 `null`
 * 變成實際使用者是非同步的（SDK 動態載入 + `onAuthStateChanged` 回調），通常發生在
 * 頁面掛載後幾百毫秒。如果這時候公開頁（例如願望牆）的請求還在飛行中，
 * `queryClient.clear()` 會把它靜默取消並整個從快取移除——該 query 的
 * `useQuery` 還訂閱著一個已經不存在的物件，狀態永遠停在 `isLoading = true`，
 * 沒有錯誤、也沒有任何會觸發重新 render 讓它復原的東西。
 *
 * 第一次解析出身分（`loading` 從 true 變 false）時跳過清除：這個時間點
 * `queryClient` 本來就是空的（剛掛載），沒有「上一個人的資料」可清，清不清
 * 差別只在於會不會誤殺還在飛行中的公開請求。之後身分真的改變（登入、登出、
 * 換人）才會照原邏輯清除。
 */
export function AuthHeaderBridge() {
  const { email, authHeaders, loading } = useAuth()
  const queryClient = useQueryClient()
  const hasResolvedAuth = useRef(false)

  useEffect(() => {
    registerAuthHeaderProvider(authHeaders)
  }, [authHeaders])

  useEffect(() => {
    if (loading) return

    if (hasResolvedAuth.current) {
      void queryClient.clear()
    }
    hasResolvedAuth.current = true
  }, [email, loading, queryClient])

  return null
}
