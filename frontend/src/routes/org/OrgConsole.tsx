import { useQuery } from '@tanstack/react-query'
import { NavLink, Outlet } from 'react-router-dom'
import { api } from '../../lib/api'
import { useAuth } from '../../lib/authContext'
import type { OrganizationView } from '../../lib/types'
import { useCurrentUser } from '../../lib/useCurrentUser'
import type { OrgContext } from './orgContext'
import { ErrorBanner, Notice, Spinner } from '../../components/Feedback'
import { OrganizationStatusBadge } from '../../components/StatusBadge'
import { OrgRegister } from './OrgRegister'

/**
 * 機構後台的外框。
 *
 * 依身分決定顯示什麼：還不是機構成員的人看到註冊表單，
 * 已註冊但未核准的看到審核狀態，核准後才看得到完整功能。
 */
export function OrgConsole() {
  const { email } = useAuth()
  const me = useCurrentUser()

  const organization = useQuery({
    queryKey: ['organization', 'me'],
    queryFn: () => api.get<OrganizationView>('/api/organizations/me'),
    enabled: me.data?.role === 'ORG_MEMBER',
  })

  if (!email) return <Notice>請先在右上角登入。</Notice>
  if (me.isLoading) return <Spinner />
  if (me.data?.role === 'ADMIN') {
    return <Notice tone="warning">管理員無法註冊或管理機構，以免球員兼裁判。</Notice>
  }
  if (me.data?.role !== 'ORG_MEMBER') return <OrgRegister />

  if (organization.isLoading) return <Spinner label="載入機構資料" />
  if (organization.isError) {
    return <ErrorBanner error={organization.error} onRetry={() => void organization.refetch()} />
  }

  const data = organization.data!

  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-3xl font-bold text-santa-700">{data.name}</h1>
          <p className="mt-1 text-sm text-slate-500">{data.contactEmail}</p>
        </div>
        <OrganizationStatusBadge status={data.status} />
      </header>

      <ReviewStatusNotice organization={data} />

      <nav className="flex flex-wrap gap-1 border-b border-santa-100">
        <Tab to="/org">願望管理</Tab>
        <Tab to="/org/claims">認領管理</Tab>
        <Tab to="/org/overdue">逾期提醒</Tab>
        <Tab to="/org/settings">機構設定</Tab>
      </nav>

      <Outlet context={{ organization: data } satisfies OrgContext} />
    </div>
  )
}

function Tab({ to, children }: { to: string; children: React.ReactNode }) {
  return (
    <NavLink
      to={to}
      end={to === '/org'}
      className={({ isActive }) =>
        `-mb-px border-b-2 px-4 py-2 text-sm font-medium transition-colors ${
          isActive
            ? 'border-santa-600 text-santa-700'
            : 'border-transparent text-slate-500 hover:text-santa-600'
        }`
      }
    >
      {children}
    </NavLink>
  )
}

function ReviewStatusNotice({ organization }: { organization: OrganizationView }) {
  if (organization.status === 'APPROVED') return null

  if (organization.status === 'PENDING') {
    return (
      <Notice tone="warning">
        機構正在審核中，核准後就能上架願望。你可以先把願望存成草稿。
      </Notice>
    )
  }

  if (organization.status === 'REJECTED') {
    return (
      <Notice tone="warning">
        <p className="font-medium">審核未通過</p>
        {organization.reviewNote && <p className="mt-1">{organization.reviewNote}</p>}
        <p className="mt-1">請至「機構設定」補齊資料後重新送審。</p>
      </Notice>
    )
  }

  return <Notice tone="warning">機構已停權，請與平台管理員聯繫。</Notice>
}
