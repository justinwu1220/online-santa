import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { api, withQuery } from '../../lib/api'
import type { OrganizationAnnualStats } from '../../lib/types'
import { AnnualStatCard, CohortNotice, YearSelect } from '../../components/AnnualStats'
import { ClaimOutcomeChart } from '../../components/charts/ClaimOutcomeChart'
import { MonthlyClaimsChart } from '../../components/charts/MonthlyClaimsChart'
import { ErrorBanner, Spinner } from '../../components/Feedback'
import { ConsolePanel } from '../../components/layouts/ConsoleLayout'

/**
 * 機構後台的年度回顧。
 *
 * 不帶 year 參數時後端自行決定「今年」並附上可選年份清單——第一次載入前不知道
 * 有哪些年份可選，所以年份下拉要等第一次回應回來才能畫出來。
 */
export function OrgAnnualReview() {
  const [year, setYear] = useState<number | undefined>(undefined)

  const stats = useQuery({
    queryKey: ['organization', 'annual-stats', year],
    queryFn: () => api.get<OrganizationAnnualStats>(
      withQuery('/api/organizations/me/stats/annual', { year })),
    // 切換年度時保留舊資料直到新的回來，不要整頁退回 Spinner——
    // 年度下拉本身也是靠 stats.data 才畫得出來，退回 Spinner 會讓它跟著消失
    placeholderData: keepPreviousData,
  })

  return (
    <div className="space-y-5">
      <ConsolePanel
        title="年度回顧"
        action={stats.data && (
          <YearSelect value={stats.data.year} years={stats.data.availableYears} onChange={setYear} />
        )}
      >
        <CohortNotice />
      </ConsolePanel>

      {stats.isLoading && <Spinner label="載入年度統計" />}
      {stats.isError && <ErrorBanner error={stats.error} onRetry={() => void stats.refetch()} />}

      {stats.data && (
        <>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <AnnualStatCard label="新增願望" value={stats.data.newWishes} />
            <AnnualStatCard label="被認領" value={stats.data.claimed} />
            <AnnualStatCard label="完成" value={stats.data.completed}
              hint={`完成率 ${Math.round(stats.data.completionRate * 100)}%`} />
            <AnnualStatCard label="釋回" value={stats.data.released}
              hint={`其中逾期自動釋回 ${stats.data.autoReleasedCount} 筆`} />
            <AnnualStatCard label="取消" value={stats.data.cancelled} />
            <AnnualStatCard label="平均完成天數"
              value={stats.data.averageCompletionDays != null
                ? stats.data.averageCompletionDays.toFixed(1) : '—'} />
            <AnnualStatCard label="跨年完成" value={stats.data.crossYearCompletions}
              hint="該年度認領、隔年才完成的筆數" />
          </div>

          <div className="grid gap-5 lg:grid-cols-2">
            <ConsolePanel title="每月認領分布">
              <MonthlyClaimsChart data={stats.data.monthlyClaims} />
            </ConsolePanel>
            <ConsolePanel title="認領結果">
              <ClaimOutcomeChart
                completed={stats.data.completed}
                released={stats.data.released}
                cancelled={stats.data.cancelled}
              />
            </ConsolePanel>
          </div>
        </>
      )}
    </div>
  )
}
