import { Outlet } from 'react-router-dom'
import { ConsoleShell, type ConsoleNavItem } from '../../components/console/ConsoleShell'
import { useAdminTeamUnread, useAdminWishCounts, usePlatformStats } from './adminQueries'

// 選單圖示：設計稿的 path（24x24）
const ICON_OVERVIEW = (
  <>
    <rect width="7" height="9" x="3" y="3" rx="1" /><rect width="7" height="5" x="14" y="3" rx="1" />
    <rect width="7" height="9" x="14" y="12" rx="1" /><rect width="7" height="5" x="3" y="16" rx="1" />
  </>
)
const ICON_ORGS = (
  <>
    <path d="M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8" />
    <path d="M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
  </>
)
const ICON_WISHES = (
  <>
    <rect x="3" y="8" width="18" height="4" rx="1" /><path d="M12 8v13" />
    <path d="M19 12v7a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-7" />
    <path d="M7.5 8a2.5 2.5 0 0 1 0-5A4.8 8 0 0 1 12 8a4.8 8 0 0 1 4.5-5 2.5 2.5 0 0 1 0 5" />
  </>
)
const ICON_CHAT = <path d="M7.9 20A9 9 0 1 0 4 16.1L2 22Z" />
const ICON_USERS = (
  <>
    <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" />
    <path d="M22 21v-2a4 4 0 0 0-3-3.87" /><path d="M16 3.13a4 4 0 0 1 0 7.75" />
  </>
)
const ICON_ANNUAL = (
  <>
    <path d="M3 3v16a2 2 0 0 0 2 2h16" /><path d="M18 17V9" /><path d="M13 17V5" /><path d="M8 17v-3" />
  </>
)
/** 個資異動紀錄：一份文件加上一支筆。 */
const ICON_CHANGES = (
  <>
    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h7" /><path d="M14 2v6h6" />
    <path d="M21.4 13.6a2 2 0 0 0-2.8 0l-4.6 4.6V21h2.8l4.6-4.6a2 2 0 0 0 0-2.8z" />
  </>
)
const ICON_SYSTEM = (
  <path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z" />
)

/**
 * 管理員後台的外框（設計稿 16）：左側選單「總覽、機構管理、願望、團隊對話、個資異動紀錄、系統與稽核」，
 * 機構管理與願望後方顯示待審核數（機構那顆只數待審申請，停權處置不計）；另外多兩項設計稿沒有的：
 * 「團隊對話」（機構在機構後台傳來的訊息要有地方回覆）
 * 與「年度營運」（既有功能，與機構後台的年度回顧同樣放在選單裡）。
 */
export function AdminLayout() {
  const stats = usePlatformStats()
  const wishes = useAdminWishCounts()
  const teamUnread = useAdminTeamUnread()

  const items: ConsoleNavItem[] = [
    { to: '/admin', label: '總覽', icon: ICON_OVERVIEW, end: true },
    { to: '/admin/orgs', label: '機構管理', icon: ICON_ORGS, badge: stats.data?.pendingOrganizations,
      badgeLabel: (n) => `${n} 間待審核` },
    { to: '/admin/wishes', label: '願望', icon: ICON_WISHES, badge: wishes.data?.review,
      badgeLabel: (n) => `${n} 個待審核` },
    { to: '/admin/users', label: '認領者', icon: ICON_USERS },
    { to: '/admin/chat', label: '團隊對話', icon: ICON_CHAT, badge: teamUnread.data?.unread,
      badgeLabel: (n) => `${n} 則機構來訊` },
    { to: '/admin/annual', label: '年度營運', icon: ICON_ANNUAL },
    { to: '/admin/data-changes', label: '個資異動紀錄', icon: ICON_CHANGES },
    { to: '/admin/system', label: '系統與稽核', icon: ICON_SYSTEM },
  ]

  return (
    <ConsoleShell appLabel="管理員後台" name="線上聖誕老人團隊" items={items}>
      <Outlet />
    </ConsoleShell>
  )
}
