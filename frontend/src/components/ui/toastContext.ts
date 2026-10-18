import { createContext, useContext } from 'react'

export type ToastTone = 'ok' | 'danger'

export interface ToastApi {
  show: (text: string, tone?: ToastTone) => void
}

export const ToastContext = createContext<ToastApi>({ show: () => {} })

/** 畫面下方的短暫提示。沒有 Provider 時什麼都不做，不會報錯。 */
export function useToast() {
  return useContext(ToastContext)
}
