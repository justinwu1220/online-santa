import { createContext, useContext } from 'react'

export interface OnboardApi {
  /** 目前這個人有首次登入說明可以看（認領者）。標頭的 ⓘ 按鈕只在這時顯示 */
  available: boolean
  /** 重看一次說明 */
  reopen: () => void
}

export const OnboardContext = createContext<OnboardApi | null>(null)

/** 取得「重看首次登入說明」的入口。沒有包在 OnboardHost 裡時回傳 null。 */
export function useOnboard() {
  return useContext(OnboardContext)
}
