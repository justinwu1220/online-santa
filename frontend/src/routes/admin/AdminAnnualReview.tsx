import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { api, withQuery } from '../../lib/api'
import type { PlatformAnnualStats } from '../../lib/types'
import { AnnualStatCard, CohortNotice, YearSelect } from '../../components/AnnualStats'
import { ClaimOutcomeChart } from '../../components/charts/ClaimOutcomeChart'
import { MonthlyClaimsChart } from '../../components/charts/MonthlyClaimsChart'
import { OrganizationRankingChart } from '../../components/charts/OrganizationRankingChart'
import { ErrorBanner, Spinner } from '../../components/Feedback'
import { ConsolePanel } from '../../components/layouts/ConsoleLayout'

/** 監控中心的年度營運總覽。機構完成排行僅在這裡出現——機構彼此看不到對方的數字。 */
export function AdminAnnualReview() {
  const [year, setYear] = useState<number | undefined>(undefined)

  const stats = useQuery({
    queryKey: ['admin', 'annual-stats', year],
    queryFn: () => api.get<PlatformAnnualStats>(withQuery('/api/admin/stats/annual', { year })),
    // 切換年度時保留舊資料直到新的回來，不要整頁退回 Spinner——
    // 年度下拉本身也是靠 stats.data 才畫得出來，退回 Spinner 會讓它跟著消失
    placeholderData: keepPreviousData,
  })

  return (
    <div className="space-y-5">
      <ConsolePanel
        title="年度營運"
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
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            <AnnualStatCard label="新捐贈者" value={stats.data.newDonors} />
            <AnnualStatCard label="新加入機構" value={stats.data.newOrganizations} />
            <AnnualStatCard label="活躍捐贈者" value={stats.data.activeDonors} hint="該年至少認領一次" />
            <AnnualStatCard label="發布願望" value={stats.data.publishedWishes} />
            <AnnualStatCard label="認領" value={stats.data.claimed} />
            <AnnualStatCard label="完成" value={stats.data.completed}
              hint={`完成率 ${Math.round(stats.data.completionRate * 100)}%`} />
          </div>

          <div className="grid gap-5 lg:grid-cols-2">
            <ConsolePanel title="每月認領趨勢">
              <MonthlyClaimsChart data={stats.data.monthlyClaims} />
            </ConsolePanel>
            <ConsolePanel title="認領結果分布">
              <ClaimOutcomeChart
                completed={stats.data.claimOutcomes.COMPLETED ?? 0}
                released={stats.data.claimOutcomes.RELEASED ?? 0}
                cancelled={stats.data.claimOutcomes.CANCELLED ?? 0}
              />
            </ConsolePanel>
          </div>

          <ConsolePanel title="機構完成排行 Top 5">
            <OrganizationRankingChart data={stats.data.topOrganizations} />
          </ConsolePanel>
        </>
      )}
    </div>
  )
}
