import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { COHORT_NOTE, MonthlyPanel, Stat, YearPicker } from '../../components/annual/AnnualParts'
import { ClaimOutcomeChart } from '../../components/charts/ClaimOutcomeChart'
import { ErrorNotice, Spinner } from '../../components/ui/Spinner'
import { api, withQuery } from '../../lib/api'
import { pageTitle } from '../../lib/brand'
import type { OrganizationAnnualStats, OrganizationMonthlyStats } from '../../lib/types'

const fetchDaily = (year: number, month: number) =>
  api.get<OrganizationMonthlyStats>(withQuery('/api/organizations/me/stats/monthly', { year, month }))

/**
 * 年度回顧。設計稿的機構後台沒有這一頁，是既有功能；放在選單第三項，樣式沿用後台面板。
 *
 * 不帶 year 參數時後端自行決定「今年」並附上可選年份清單——第一次載入前不知道
 * 有哪些年份可選，所以年份下拉要等第一次回應回來才能畫出來。
 */
export function OrgAnnualReview() {
  const [year, setYear] = useState<number | undefined>(undefined)

  useEffect(() => {
    document.title = pageTitle('年度回顧｜機構後台')
    return () => { document.title = pageTitle() }
  }, [])

  const stats = useQuery({
    queryKey: ['organization', 'annual-stats', year],
    queryFn: () => api.get<OrganizationAnnualStats>(
      withQuery('/api/organizations/me/stats/annual', { year })),
    // 切換年度時保留舊資料直到新的回來，年度下拉本身也是靠 stats.data 才畫得出來
    placeholderData: keepPreviousData,
  })

  return (
    <section className="org-tab" aria-labelledby="orgAnnualTitle">
      <div className="org-head">
        <h1 id="orgAnnualTitle">年度回顧</h1>
        {stats.data && <YearPicker value={stats.data.year} years={stats.data.availableYears} onChange={setYear} />}
      </div>

      <div className="annual">
        <p className="annual__note">{COHORT_NOTE}</p>

        {stats.isLoading && <Spinner label="載入年度統計" />}
        {stats.isError && <ErrorNotice error={stats.error} onRetry={() => void stats.refetch()} />}

        {stats.data && (
          <>
            <div className="annual__stats">
              <Stat label="新增願望" value={stats.data.newWishes} />
              <Stat label="被認領" value={stats.data.claimed} />
              <Stat label="完成" value={stats.data.completed}
                hint={`完成率 ${Math.round(stats.data.completionRate * 100)}%`} />
              <Stat label="釋回" value={stats.data.released}
                hint={`其中逾期自動釋回 ${stats.data.autoReleasedCount} 筆`} />
              <Stat label="取消" value={stats.data.cancelled} />
              <Stat label="平均完成天數"
                value={stats.data.averageCompletionDays != null
                  ? stats.data.averageCompletionDays.toFixed(1) : '—'} />
              <Stat label="跨年完成" value={stats.data.crossYearCompletions}
                hint="該年度認領、隔年才完成的筆數" />
            </div>

            <div className="annual__charts">
              <MonthlyPanel key={stats.data.year} scope="org" year={stats.data.year} title="每月認領分布"
                monthly={stats.data.monthlyClaims} fetchDaily={fetchDaily} />
              <section className="panel">
                <div className="panel__head"><h2 className="panel__title">認領結果</h2></div>
                <ClaimOutcomeChart theme="dark" completed={stats.data.completed}
                  released={stats.data.released} cancelled={stats.data.cancelled} />
              </section>
            </div>
          </>
        )}
      </div>
    </section>
  )
}
