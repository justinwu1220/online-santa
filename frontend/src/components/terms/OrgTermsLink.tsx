import { TermsLink } from './TermsLink'
import { ORG_TERMS } from './termsContent'

/** 「《合作機構參與須知》」連結：機構首次說明、機構後台左下角等處用。 */
export function OrgTermsLink() {
  return <TermsLink terms={ORG_TERMS} />
}
