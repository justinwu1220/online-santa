import { useState } from 'react'
import { isBundleCurrent, useAcceptDonorTerms, useDonorTerms } from '../../lib/terms'
import { TermsDialog } from './TermsDialog'
import { DONOR_TERMS } from './termsContent'

/**
 * 「《認領者參與須知》」連結。點了開啟須知全文，開關狀態自己管，
 * 任何地方（認領確認、個人資料…）一行就能用：`認領前請再看一次<DonorTermsLink />。`
 *
 * 已同意目前版本 → 唯讀版（顯示同意時間）；沒同意過、或同意的是舊版本 → 需要（重新）同意的版本
 * （SPEC 15.8）。沒登入時只能看全文。
 */
export function DonorTermsLink() {
  const [open, setOpen] = useState(false)
  const status = useDonorTerms()
  const accept = useAcceptDonorTerms()
  const needsConsent = Boolean(status.data) && !status.data!.upToDate

  return (
    <>
      <button type="button" className="terms-link" onClick={() => setOpen(true)}>
        《{DONOR_TERMS.title}》
      </button>
      <TermsDialog
        open={open}
        onClose={() => setOpen(false)}
        terms={DONOR_TERMS}
        mode={needsConsent ? 'consent' : 'readonly'}
        acceptedAt={status.data?.upToDate ? status.data.acceptedAt : undefined}
        acceptLabel="同意並繼續"
        outdated={!isBundleCurrent(status.data)}
        onAccept={async () => { await accept(); setOpen(false) }}
      />
    </>
  )
}
