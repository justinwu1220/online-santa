import { Clock } from 'lucide-react'
import { Outlet } from 'react-router-dom'
import { ConsoleShell, type ConsoleNavItem } from '../../components/console/ConsoleShell'
import { OrgTermsGate } from '../../components/terms/OrgTermsGate'
import { TermsLink } from '../../components/terms/TermsLink'
import { ORG_TERMS } from '../../components/terms/termsContent'
import { Callout } from '../../components/ui/Callout'
import { ErrorNotice, Spinner } from '../../components/ui/Spinner'
import type { OrganizationView } from '../../lib/types'
import { useOrganization, useOrgUnread } from './orgQueries'

// 選單圖示：設計稿的 path（24x24）
const ICON_WISHES = (
  <>
    <rect x="3" y="8" width="18" height="4" rx="1" /><path d="M12 8v13" />
    <path d="M19 12v7a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-7" />
    <path d="M7.5 8a2.5 2.5 0 0 1 0-5A4.8 8 0 0 1 12 8a4.8 8 0 0 1 4.5-5 2.5 2.5 0 0 1 0 5" />
  </>
)
const ICON_CHAT = <path d="M7.9 20A9 9 0 1 0 4 16.1L2 22Z" />
const ICON_ANNUAL = (
  <>
    <path d="M3 3v16a2 2 0 0 0 2 2h16" /><path d="M18 17V9" /><path d="M13 17V5" /><path d="M8 17v-3" />
  </>
)
const ICON_SETTINGS = (
  <>
    <path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z" />
    <circle cx="12" cy="12" r="3" />
  </>
)
const ICON_TERMS = (
  <>
    <path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z" /><path d="M14 2v4a2 2 0 0 0 2 2h4" />
    <path d="M10 9H8" /><path d="M16 13H8" /><path d="M16 17H8" />
  </>
)

/**
 * 機構後台的外框（設計稿 15.2）：左側選單「願望管理、與團隊對話、機構設定」（前兩項後方紅色未讀數），
 * 另外多一項「年度回顧」——那是既有功能，設計稿的選單沒有，經使用者確認放在選單裡。
 * 資料（機構本身）在這裡查一次，給底下各頁共用。
 */
export function OrgLayout() {
  const organization = useOrganization()
  const unread = useOrgUnread()

  if (organization.isLoading) return <div className="rd"><main className="page"><Spinner label="載入機構資料" /></main></div>
  if (organization.isError) {
    return (
      <div className="rd"><main className="page">
        <ErrorNotice error={organization.error} onRetry={() => void organization.refetch()} />
      </main></div>
    )
  }
  const data = organization.data!

  const items: ConsoleNavItem[] = [
    { to: '/org', label: '願望管理', icon: ICON_WISHES, badge: unread.data?.wishes, end: true },
    { to: '/org/chat', label: '與團隊對話', icon: ICON_CHAT, badge: unread.data?.team },
    { to: '/org/annual', label: '年度回顧', icon: ICON_ANNUAL },
    { to: '/org/settings', label: '機構設定', icon: ICON_SETTINGS },
  ]

  return (
    <ConsoleShell
      appLabel="機構後台"
      name={data.name}
      items={items}
      banner={<ReviewBanner organization={data} />}
      terms={
        <TermsLink terms={ORG_TERMS} className="org-side__terms">
          <svg className="icon" viewBox="0 0 24 24" aria-hidden>{ICON_TERMS}</svg>
          合作機構參與須知
        </TermsLink>
      }
    >
      <Outlet context={{ organization: data }} />
      <OrgTermsGate />
    </ConsoleShell>
  )
}

/** 還沒通過審核（或被退回、停權）時，內容區最上方的提示。 */
function ReviewBanner({ organization }: { organization: OrganizationView }) {
  if (organization.status === 'APPROVED') return null

  if (organization.status === 'PENDING') {
    return (
      <Callout tone="warn" className="org-review" icon={<Clock className="icon" aria-hidden />}>
        <strong>機構資料審核中。</strong>審核通過前，願望可以先存成草稿；通過後就能送出刊登。
        審核結果會寄到 <strong>{organization.contactEmail}</strong>。
      </Callout>
    )
  }
  if (organization.status === 'REJECTED') {
    return (
      <Callout tone="warn" className="org-review" icon={<Clock className="icon" aria-hidden />}>
        <strong>機構資料審核未通過。</strong>
        {organization.reviewNote && <>原因：{organization.reviewNote}。</>}
        請至「機構設定」補齊資料後重新送審。
      </Callout>
    )
  }
  return (
    <Callout tone="warn" className="org-review" icon={<Clock className="icon" aria-hidden />}>
      <strong>機構已停權。</strong>已上架與已認領的願望不受影響，但無法建立新願望；請與平台管理員聯繫。
    </Callout>
  )
}
