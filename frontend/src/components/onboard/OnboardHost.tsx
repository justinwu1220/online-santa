import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { api } from '../../lib/api'
import { useAuth } from '../../lib/authContext'
import { useDonorTerms } from '../../lib/terms'
import { useCurrentUser } from '../../lib/useCurrentUser'
import { useMyProfile } from '../../lib/useMyProfile'
import { useToast } from '../ui/toastContext'
import { DonorTermsLink } from '../terms/DonorTermsLink'
import { OnboardContext, type OnboardApi } from './onboardContext'
import { OnboardDialog } from './OnboardDialog'
import { DONOR_ONBOARD_CARDS, DONOR_ONBOARD_ILLUSTRATIONS } from './onboardContent'

/** 驗證完成回到首頁後，等一下再跳出說明（SPEC 13.2：約 1.1 秒），讓頁面先安頓好。 */
const AUTO_OPEN_DELAY_MS = 1100

/**
 * 首次登入說明（認領者）：負責何時自動跳出、看完如何記錄，也讓標頭的 ⓘ 可以重看。
 *
 * 自動跳出的條件：已登入、資料庫角色是認領者、**信箱已驗證**、後端記錄還沒看過、
 * 而且已經同意目前版本的須知（避免和須知視窗同時跳出兩個；也自然排除沒簽認領者須知的機構註冊者）。
 * 涵蓋「按我已經驗證好了回到首頁」，以及「在別的分頁點了驗證信、之後才回來」。
 */
export function OnboardHost({ children }: { children: ReactNode }) {
  const { email, emailVerified } = useAuth()
  const user = useCurrentUser()
  const profile = useMyProfile()
  const terms = useDonorTerms()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [auto, setAuto] = useState(false)
  const [manual, setManual] = useState(false)

  const isDonor = user.data?.role === 'DONOR'
  const eligible = Boolean(email) && emailVerified && isDonor
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
    toast.show('準備好了！挑一個願望開始吧')
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
        cards={DONOR_ONBOARD_CARDS}
        illustrations={DONOR_ONBOARD_ILLUSTRATIONS}
        // 第一次看不能略過；使用者自己按 ⓘ 重看時可以直接關掉
        dismissible={manual && !auto}
        // 說明裡提到的《認領者參與須知》點了就開啟須知全文（疊在這個彈窗上方）
        links={{ '《認領者參與須知》': <DonorTermsLink /> }}
        onClose={() => setManual(false)}
        onFinish={finish}
      />
    </OnboardContext.Provider>
  )
}
