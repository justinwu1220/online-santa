import { useState } from 'react'
import { TermsDialog } from './TermsDialog'
import { DONOR_TERMS } from './termsContent'

/**
 * 「《認領者參與須知》」連結。點了開啟須知全文，開關狀態自己管，
 * 任何地方（認領確認、註冊、個人資料…）一行就能用：`認領前請再看一次<DonorTermsLink />。`
 */
export function DonorTermsLink() {
  const [open, setOpen] = useState(false)
  return (
    <>
      <button type="button" className="terms-link" onClick={() => setOpen(true)}>
        《{DONOR_TERMS.title}》
      </button>
      <TermsDialog open={open} onClose={() => setOpen(false)} terms={DONOR_TERMS} />
    </>
  )
}
