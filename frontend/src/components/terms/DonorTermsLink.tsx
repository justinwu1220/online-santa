import { TermsLink } from './TermsLink'
import { DONOR_TERMS } from './termsContent'

/** 「《認領者參與須知》」連結：認領確認、個人資料、首次說明等處用。 */
export function DonorTermsLink() {
  return <TermsLink terms={DONOR_TERMS} />
}
