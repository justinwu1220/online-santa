import { useState } from 'react'
import { useAuth } from '../../lib/authContext'
import { isBundleCurrent, termsFlow, useAcceptDonorTerms, useDonorTerms } from '../../lib/terms'
import { effectiveRoleOf, useCurrentUser } from '../../lib/useCurrentUser'
import { TermsDialog } from './TermsDialog'
import { DONOR_TERMS } from './termsContent'

const DISMISSED_KEY = 'online-santa.terms-gate-dismissed'

function readDismissed(): string | null {
  try { return sessionStorage.getItem(DISMISSED_KEY) } catch { return null }
}

/**
 * 守門：登入的認領者還沒同意目前版本的須知時，自動跳出同意視窗。
 *
 * 涵蓋四種人：功能上線前就註冊的、用 Google 登入的、須知改版後需要重新同意的、
 * 以及註冊時同意紀錄剛好沒寫成功的。取消後這個分頁不再自動跳（避免每換一頁就擋一次），
 * 但認領時一定會再攔（見 WishDetail），後端也會擋。
 *
 * 前端打包的須知版本和後端不一致時不自動跳（跳了也不能同意），認領時才提示重新整理。
 */
export function TermsGate() {
  const { email } = useAuth()
  const user = useCurrentUser()
  const status = useDonorTerms()
  const accept = useAcceptDonorTerms()
  const [dismissedFor, setDismissedFor] = useState<string | null>(readDismissed)

  const isDonor = effectiveRoleOf(user.data) === 'DONOR'
  const open = Boolean(email)
    && isDonor
    && Boolean(status.data)
    && !status.data!.upToDate
    && isBundleCurrent(status.data)
    && !termsFlow.registering
    && dismissedFor !== email

  function dismiss() {
    setDismissedFor(email)
    try { if (email) sessionStorage.setItem(DISMISSED_KEY, email) } catch { /* 無痕視窗：本次再跳一次也無妨 */ }
  }

  return (
    <TermsDialog
      open={open}
      onClose={dismiss}
      terms={DONOR_TERMS}
      mode="consent"
      acceptLabel="同意並繼續"
      onAccept={accept}
    />
  )
}
