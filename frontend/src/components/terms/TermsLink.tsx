import { useState } from 'react'
import { isBundleCurrent, useAcceptTerms, useTermsStatus } from '../../lib/terms'
import { TermsDialog } from './TermsDialog'
import type { TermsDocument } from './termsContent'

/**
 * 「《○○參與須知》」連結。點了開啟須知全文，開關狀態自己管，任何地方一行就能用。
 *
 * 已同意目前版本 → 唯讀版（顯示同意時間）；沒同意過、或同意的是舊版本 → 需要（重新）同意的版本
 * （SPEC 15.8）。沒登入時只能看全文。認領者與合作機構各有一份須知，共用這個元件。
 */
export function TermsLink({ terms, acceptLabel = '同意並繼續' }: {
  terms: TermsDocument
  acceptLabel?: string
}) {
  const [open, setOpen] = useState(false)
  const status = useTermsStatus(terms)
  const accept = useAcceptTerms(terms)
  const needsConsent = Boolean(status.data) && !status.data!.upToDate

  return (
    <>
      <button type="button" className="terms-link" onClick={() => setOpen(true)}>
        《{terms.title}》
      </button>
      <TermsDialog
        open={open}
        onClose={() => setOpen(false)}
        terms={terms}
        mode={needsConsent ? 'consent' : 'readonly'}
        agreed={status.data?.upToDate === true}
        acceptedAt={status.data?.upToDate ? status.data.acceptedAt : undefined}
        acceptLabel={acceptLabel}
        outdated={!isBundleCurrent(status.data, terms)}
        onAccept={async () => { await accept(); setOpen(false) }}
      />
    </>
  )
}
