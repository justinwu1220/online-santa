import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback } from 'react'
import { DONOR_TERMS } from '../components/terms/termsContent'
import { api } from './api'
import { useAuth } from './authContext'

/** 後端回傳的某份須知對目前使用者的狀態（見後端 TermsStatusView）。 */
export interface TermsStatus {
  type: 'DONOR'
  title: string
  /** 後端目前的版本 */
  currentVersion: string
  /** 最近一次同意的版本，沒同意過為 undefined（可能比目前版本舊） */
  acceptedVersion?: string
  acceptedAt?: string
  /** 最近一次同意的就是目前版本 */
  upToDate: boolean
}

/**
 * 註冊流程進行中的旗標。註冊剛成功、同意紀錄還在送出的那一小段時間，帳號已經登入但紀錄還沒寫入，
 * 守門（TermsGate）看到「還沒同意」會多跳出第二個視窗；有這個旗標它就先不動。
 */
export const termsFlow = { registering: false }

/** 認領者須知的同意狀態。沒登入時不查。 */
export function useDonorTerms() {
  const { email } = useAuth()
  return useQuery({
    queryKey: ['terms', email],
    queryFn: async () => {
      const all = await api.get<TermsStatus[]>('/api/me/terms')
      return all.find((item) => item.type === 'DONOR') ?? null
    },
    enabled: Boolean(email),
    staleTime: 60_000,
  })
}

/**
 * 前端打包的須知內容是否就是後端目前的版本。
 *
 * 不一致（後端已改版、網站還是舊的前端）時不能讓使用者「同意」：他看到的是舊文字，
 * 同意紀錄卻會寫成新版本。
 */
export function isBundleCurrent(status?: TermsStatus | null) {
  return !status || status.currentVersion === DONOR_TERMS.version
}

/** 送出同意（版本是畫面上這份須知自己的版本），成功後重新查詢狀態。 */
export function useAcceptDonorTerms() {
  const queryClient = useQueryClient()
  return useCallback(async () => {
    await api.post<TermsStatus>('/api/me/terms/DONOR/accept', { version: DONOR_TERMS.version })
    await queryClient.invalidateQueries({ queryKey: ['terms'] })
  }, [queryClient])
}
