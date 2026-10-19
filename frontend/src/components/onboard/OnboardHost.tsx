import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { api } from '../../lib/api'
import { useAuth } from '../../lib/authContext'
import { useTermsStatus } from '../../lib/terms'
import { useCurrentUser } from '../../lib/useCurrentUser'
import { useMyProfile } from '../../lib/useMyProfile'
import { useToast } from '../ui/toastContext'
import { DonorTermsLink } from '../terms/DonorTermsLink'
import { OrgTermsLink } from '../terms/OrgTermsLink'
import { DONOR_TERMS, ORG_TERMS } from '../terms/termsContent'
import { OnboardContext, type OnboardApi } from './onboardContext'
import { OnboardDialog } from './OnboardDialog'
import {
  DONOR_ONBOARD_CARDS, DONOR_ONBOARD_ILLUSTRATIONS, ORG_ONBOARD_CARDS, ORG_ONBOARD_ILLUSTRATIONS,
} from './onboardContent'

/** 驗證完成回到首頁後，等一下再跳出說明（SPEC 13.2：約 1.1 秒），讓頁面先安頓好。 */
const AUTO_OPEN_DELAY_MS = 1100

/**
 * 首次登入說明：認領者 5 張、合作機構 8 張。負責何時自動跳出、看完如何記錄；
 * 認領者的標頭 ⓘ 可以重看（機構版原型沒有這個入口）。
 *
 * 自動跳出的條件：已登入、**信箱已驗證**、後端記錄還沒看過、而且已經同意自己那份須知的目前版本
 * （避免和須知視窗同時跳出兩個）。認領者涵蓋「按我已經驗證好了回到首頁」與「在別的分頁點了驗證信、
 * 之後才回來」；機構則是送出申請、成為機構成員（進到機構後台）之後才符合，只驗證信箱、還沒送出申請的
 * 人仍是認領者身分，不會跳出機構版（SPEC 13.2）。
 */
export function OnboardHost({ children }: { children: ReactNode }) {
  const { email, emailVerified } = useAuth()
  const user = useCurrentUser()
  const profile = useMyProfile()
  const donorTerms = useTermsStatus(DONOR_TERMS)
  const orgTerms = useTermsStatus(ORG_TERMS)
  const toast = useToast()
  const queryClient = useQueryClient()
  const [auto, setAuto] = useState(false)
  const [manual, setManual] = useState(false)

  const isDonor = user.data?.role === 'DONOR'
  const isOrg = user.data?.role === 'ORG_MEMBER'
  const terms = isOrg ? orgTerms : donorTerms
  const eligible = Boolean(email) && emailVerified && (isDonor || isOrg)
    && profile.data !== undefined && !profile.data.onboarded
    && terms.data?.upToDate === true

  useEffect(() => {
    if (!eligible) return
    const timer = window.setTimeout(() => setAuto(true), AUTO_OPEN_DELAY_MS)
    return () => window.clearTimeout(timer)
  }, [eligible])

  function finish() {
    setAuto(false)
    setManual(false)
    toast.show(isOrg ? '準備好了！審核通過後就能開始刊登願望' : '準備好了！挑一個願望開始吧')
    // 記錄「看完了」。失敗不影響使用，頂多下次再看一次
    api.post('/api/me/onboarded')
      .then(() => queryClient.invalidateQueries({ queryKey: ['me', 'profile'] }))
      .catch((error: unknown) => console.warn('記錄首次登入說明失敗', error))
  }

  const value = useMemo<OnboardApi>(
    () => ({ available: Boolean(email) && isDonor, reopen: () => setManual(true) }),
    [email, isDonor])

  return (
    <OnboardContext.Provider value={value}>
      {children}
      <OnboardDialog
        open={auto || manual}
        cards={isOrg ? ORG_ONBOARD_CARDS : DONOR_ONBOARD_CARDS}
        illustrations={isOrg ? ORG_ONBOARD_ILLUSTRATIONS : DONOR_ONBOARD_ILLUSTRATIONS}
        // 第一次看不能略過；使用者自己按 ⓘ 重看時可以直接關掉
        dismissible={manual && !auto}
        // 說明裡提到的須知點了就開啟須知全文（疊在這個彈窗上方）
        links={isOrg
          ? { '《合作機構參與須知》': <OrgTermsLink /> }
          : { '《認領者參與須知》': <DonorTermsLink /> }}
        onClose={() => setManual(false)}
        onFinish={finish}
      />
    </OnboardContext.Provider>
  )
}
