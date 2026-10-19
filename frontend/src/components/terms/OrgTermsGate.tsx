import { useState } from 'react'
import { useAuth } from '../../lib/authContext'
import { isBundleCurrent, useAcceptTerms, useTermsStatus } from '../../lib/terms'
import { TermsDialog } from './TermsDialog'
import { ORG_TERMS } from './termsContent'

const DISMISSED_KEY = 'online-santa.org-terms-gate-dismissed'

function readDismissed(): string | null {
  try { return sessionStorage.getItem(DISMISSED_KEY) } catch { return null }
}

/**
 * 機構後台的守門：機構成員還沒同意目前版本的《合作機構參與須知》時跳出同意視窗。
 *
 * 須知功能上線前就建立的機構（以及改版後）沒有同意紀錄。取消後本次分頁不再自動跳，
 * 左下角「合作機構參與須知」隨時可以重新開啟並同意。
 */
export function OrgTermsGate() {
  const { email } = useAuth()
  const status = useTermsStatus(ORG_TERMS)
  const accept = useAcceptTerms(ORG_TERMS)
  const [dismissedFor, setDismissedFor] = useState<string | null>(readDismissed)

  const open = Boolean(email)
    && Boolean(status.data)
    && !status.data!.upToDate
    && isBundleCurrent(status.data, ORG_TERMS)
    && dismissedFor !== email

  function dismiss() {
    setDismissedFor(email)
    try { if (email) sessionStorage.setItem(DISMISSED_KEY, email) } catch { /* 本次再跳一次也無妨 */ }
  }

  return (
    <TermsDialog open={open} onClose={dismiss} terms={ORG_TERMS} mode="consent"
      acceptLabel="同意並繼續" onAccept={accept} />
  )
}
