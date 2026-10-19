import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { COHORT_NOTE, MonthlyPanel, Stat, YearPicker } from '../../components/annual/AnnualParts'
import { ClaimOutcomeChart } from '../../components/charts/ClaimOutcomeChart'
import { OrganizationRankingChart } from '../../components/charts/OrganizationRankingChart'
import { ErrorNotice, Spinner } from '../../components/ui/Spinner'
import { api, withQuery } from '../../lib/api'
import { pageTitle } from '../../lib/brand'
import type { PlatformAnnualStats, PlatformMonthlyStats } from '../../lib/types'

const fetchDaily = (year: number, month: number) =>
  api.get<PlatformMonthlyStats>(withQuery('/api/admin/stats/monthly', { year, month }))

/**
 * 年度營運。設計稿的管理員後台沒有這一頁，是既有功能；放在選單「願望」之後，樣式沿用後台面板。
 * 機構完成排行僅在這裡出現——機構彼此看不到對方的數字。
 */
export function AdminAnnualReview() {
  const [year, setYear] = useState<number | undefined>(undefined)

  useEffect(() => {
    document.title = pageTitle('年度營運｜管理員後台')
    return () => { document.title = pageTitle() }
  }, [])

  const stats = useQuery({
    queryKey: ['admin', 'annual-stats', year],
    queryFn: () => api.get<PlatformAnnualStats>(withQuery('/api/admin/stats/annual', { year })),
    placeholderData: keepPreviousData,
  })

  return (
    <section className="org-tab" aria-labelledby="adAnnualTitle">
      <div className="org-head">
        <h1 id="adAnnualTitle">年度營運</h1>
        {stats.data && <YearPicker value={year ?? stats.data.year} years={stats.data.availableYears} onChange={setYear} />}
      </div>

      <div className="annual" aria-busy={stats.isPlaceholderData} style={stats.isPlaceholderData ? { opacity: 0.55, transition: 'opacity .2s' } : { transition: 'opacity .2s' }}>
        <p className="annual__note">{COHORT_NOTE}</p>

        {stats.isLoading && <Spinner label="載入年度統計" />}
        {stats.isError && <ErrorNotice error={stats.error} onRetry={() => void stats.refetch()} />}

        {stats.data && (
          <>
            <div className="annual__stats">
              <Stat label="新捐贈者" value={stats.data.newDonors} />
              <Stat label="新加入機構" value={stats.data.newOrganizations} />
              <Stat label="活躍捐贈者" value={stats.data.activeDonors} hint="該年至少認領一次" />
              <Stat label="發布願望" value={stats.data.publishedWishes} />
              <Stat label="認領" value={stats.data.claimed} />
              <Stat label="完成" value={stats.data.completed}
                hint={`完成率 ${Math.round(stats.data.completionRate * 100)}%`} />
            </div>

            <div className="annual__charts">
              <MonthlyPanel key={stats.data.year} scope="admin" year={stats.data.year} title="每月認領趨勢"
                monthly={stats.data.monthlyClaims} fetchDaily={fetchDaily} />
              <section className="panel">
                <div className="panel__head"><h2 className="panel__title">認領結果分布</h2></div>
                <ClaimOutcomeChart theme="dark"
                  completed={stats.data.claimOutcomes.COMPLETED ?? 0}
                  released={stats.data.claimOutcomes.RELEASED ?? 0}
                  cancelled={stats.data.claimOutcomes.CANCELLED ?? 0} />
              </section>
            </div>

            <section className="panel">
              <div className="panel__head"><h2 className="panel__title">機構完成排行 Top 5</h2></div>
              <OrganizationRankingChart theme="dark" data={stats.data.topOrganizations} />
            </section>
          </>
        )}
      </div>
    </section>
  )
}
