import { useQuery } from '@tanstack/react-query'
import { Outlet } from 'react-router-dom'
import { api, withQuery } from '../../lib/api'
import type { ClaimOrgView, OrganizationView, PageResponse } from '../../lib/types'
import { ConsoleLayout } from '../../components/layouts/ConsoleLayout'
import { ErrorBanner, Notice, Spinner } from '../../components/Feedback'
import { OrganizationStatusBadge } from '../../components/StatusBadge'

/**
 * 機構後台的外框。
 *
 * 導覽列**沒有願望牆**——機構的工作是管理自己上架的願望與處理認領，
 * 瀏覽別人的願望對他們沒有意義。要看公開頁面可以從主網站進去。
 */
export function OrgLayout() {
  const organization = useQuery({
    queryKey: ['organization', 'me'],
    queryFn: () => api.get<OrganizationView>('/api/organizations/me'),
  })

  // 逾期筆數放在導覽上，機構一進來就知道有沒有要處理的事
  const overdue = useQuery({
    queryKey: ['claims', 'org', 'overdue-count'],
    queryFn: () => api.get<PageResponse<ClaimOrgView>>(
      withQuery('/api/organizations/me/claims/overdue', { size: 1 })),
  })

  if (organization.isLoading) return <Spinner label="載入機構資料" />
  if (organization.isError) {
    return (
      <div className="mx-auto max-w-lg py-12">
        <ErrorBanner error={organization.error} onRetry={() => void organization.refetch()} />
      </div>
    )
  }

  const data = organization.data!

  return (
    <ConsoleLayout
      title="機構後台"
      subtitle={data.name}
      accent="santa"
      homePath="/org"
      items={[
        { to: '/org', label: '總覽', end: true },
        { to: '/org/wishes', label: '願望管理' },
        { to: '/org/claims', label: '認領管理' },
        { to: '/org/overdue', label: '逾期提醒', badge: overdue.data?.totalElements },
        { to: '/org/annual', label: '年度回顧' },
        { to: '/org/settings', label: '機構設定' },
      ]}
    >
      <div className="space-y-5">
        <div className="flex items-center gap-3">
          <OrganizationStatusBadge status={data.status} />
          <ReviewStatusNotice organization={data} />
        </div>
        <Outlet context={{ organization: data }} />
      </div>
    </ConsoleLayout>
  )
}

function ReviewStatusNotice({ organization }: { organization: OrganizationView }) {
  if (organization.status === 'APPROVED') return null

  if (organization.status === 'PENDING') {
    return (
      <span className="text-sm text-slate-600">
        審核中——核准後就能上架願望，現在可以先把願望存成草稿。
      </span>
    )
  }

  if (organization.status === 'REJECTED') {
    return (
      <span className="text-sm text-berry-600">
        審核未通過{organization.reviewNote && `：${organization.reviewNote}`}
        　請至「機構設定」補齊資料後重新送審。
      </span>
    )
  }

  return <span className="text-sm text-berry-600">機構已停權，請與平台管理員聯繫。</span>
}

/** 給還沒通過審核就想上架的機構看的提示。 */
export function NotApprovedNotice() {
  return (
    <Notice tone="warning">
      機構尚未通過審核，可以先建立草稿，核准後再上架。
    </Notice>
  )
}
