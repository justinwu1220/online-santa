import { useEffect, useState } from 'react'
import { COHORT_NOTE, MonthlyPanel, PeriodPicker, Stat } from '../../components/annual/AnnualParts'
import { usePeriodList, usePeriodStats } from '../../components/annual/periodQueries'
import { ClaimOutcomeChart } from '../../components/charts/ClaimOutcomeChart'
import { ErrorNotice, Spinner } from '../../components/ui/Spinner'
import { api, withQuery } from '../../lib/api'
import { pageTitle } from '../../lib/brand'
import type { OrganizationMonthlyStats, OrganizationPeriodStats } from '../../lib/types'

const fetchDaily = (year: number, month: number) =>
  api.get<OrganizationMonthlyStats>(withQuery('/api/organizations/me/stats/monthly', { year, month }))

/**
 * 檔期回顧。設計稿的機構後台沒有這一頁，是既有功能；放在選單第三項，樣式沿用後台面板。
 *
 * 期間是活動檔期的營運週期，由管理員設定；機構只能選期間、看不到檔期的其他設定。
 * 預設選項由後端決定（涵蓋今天的檔期），所以選單要等第一次回應才畫得出來。
 */
export function OrgAnnualReview() {
  const [selected, setSelected] = useState<string>()

  useEffect(() => {
    document.title = pageTitle('年度回顧｜機構後台')
    return () => { document.title = pageTitle() }
  }, [])

  const periods = usePeriodList('organization')
  const options = periods.data?.periods
  const key = options?.some((item) => item.key === selected) ? selected : periods.data?.defaultKey
  const stats = usePeriodStats<OrganizationPeriodStats>('organization', key)

  return (
    <section className="org-tab" aria-labelledby="orgAnnualTitle">
      <div className="org-head">
        <h1 id="orgAnnualTitle">年度回顧</h1>
        {options && key && <PeriodPicker value={key} periods={options} onChange={setSelected} />}
      </div>

      <div className="annual" aria-busy={stats.isPlaceholderData} style={stats.isPlaceholderData ? { opacity: 0.55, transition: 'opacity .2s' } : { transition: 'opacity .2s' }}>
        <p className="annual__note">{COHORT_NOTE}</p>

        {stats.isLoading && <Spinner label="載入統計" />}
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
              <Stat label="檔期後完成" value={stats.data.completedAfterPeriod}
                hint="該檔期認領、檔期結束後才完成的筆數" />
            </div>

            <div className="annual__charts">
              <MonthlyPanel key={stats.data.period.key} scope="org" title="每月認領分布"
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
