import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { COHORT_NOTE, MonthlyPanel, PeriodPicker, Stat } from '../../components/annual/AnnualParts'
import { usePeriodList, usePeriodStats } from '../../components/annual/periodQueries'
import { ClaimOutcomeChart } from '../../components/charts/ClaimOutcomeChart'
import { OrganizationRankingChart } from '../../components/charts/OrganizationRankingChart'
import { ErrorNotice, Spinner } from '../../components/ui/Spinner'
import { api, withQuery } from '../../lib/api'
import { pageTitle } from '../../lib/brand'
import type { CampaignView, PlatformMonthlyStats, PlatformPeriodStats } from '../../lib/types'
import { CampaignDialog } from './CampaignDialog'
import { CampaignPanel, ReceiptsPanel } from './ClosingPanels'
import { useCampaigns } from './closingQueries'

const fetchDaily = (year: number, month: number) =>
  api.get<PlatformMonthlyStats>(withQuery('/api/admin/stats/monthly', { year, month }))

type TabKey = 'stats' | 'campaign' | 'receipts'

const TABS: { key: TabKey; label: string }[] = [
  { key: 'stats', label: '營運統計' },
  { key: 'campaign', label: '檔期設定' },
  { key: 'receipts', label: '收據' },
]

/**
 * 年度營運：一年一次的活動檔期是統計與結案文件的共同單位。
 *
 * 上方選檔期（統計期間與收據歸屬同一定義：本檔期勸募開始日到下個檔期勸募開始日之前，最後一個檔期往後到今天；
 * 完全沒有檔期時才是「尚未建立檔期」），下方三個分頁：營運統計、檔期設定、收據。後兩者只在選了真正的檔期時出現。
 * 機構完成排行僅在這裡出現——機構彼此看不到對方的數字。分頁記在網址的 `?tab=`，舊的結案文件網址
 * 轉到 `?tab=receipts`。
 */
export function AdminAnnualReview() {
  const [params, setParams] = useSearchParams()
  const [selected, setSelected] = useState<string>()
  const [editing, setEditing] = useState<CampaignView | 'new' | null>(null)

  useEffect(() => {
    document.title = pageTitle('年度營運｜管理員後台')
    return () => { document.title = pageTitle() }
  }, [])

  const periods = usePeriodList('admin')
  const campaigns = useCampaigns()
  // 選過的期間若因為修改檔期而消失（例如日期改動後「尚未建立檔期」不見了），退回預設選項
  const options = periods.data?.periods
  const key = options?.some((item) => item.key === selected) ? selected : periods.data?.defaultKey
  const period = options?.find((item) => item.key === key)
  const campaign = campaigns.data?.find((item) => item.id === period?.campaignId)
  const stats = usePeriodStats<PlatformPeriodStats>('admin', key)

  const requested = params.get('tab')
  const tab: TabKey = campaign && (requested === 'campaign' || requested === 'receipts') ? requested : 'stats'
  const visibleTabs = campaign ? TABS : TABS.slice(0, 1)

  function selectTab(next: TabKey) {
    setParams(next === 'stats' ? {} : { tab: next }, { replace: true })
  }

  return (
    <section className="org-tab" aria-labelledby="adAnnualTitle">
      <div className="org-head">
        <h1 id="adAnnualTitle">年度營運</h1>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {options && key && <PeriodPicker value={key} periods={options} onChange={setSelected} />}
          <button type="button" className="btn btn--sm" onClick={() => setEditing('new')}>新增活動檔期</button>
        </div>
      </div>

      {periods.isLoading && <Spinner label="載入檔期" />}
      {periods.isError && <ErrorNotice error={periods.error} onRetry={() => void periods.refetch()} />}

      {period?.unassigned && (
        <p className="org-empty">
          還沒有活動檔期。建立檔期後，機構才能在上架期間內新增願望，統計與收據也會按檔期歸檔。
        </p>
      )}

      {visibleTabs.length > 1 && (
        <div className="drawer__tabs" role="tablist" aria-label="切換內容" style={{ padding: 0 }}>
          {visibleTabs.map((item) => (
            <button key={item.key} type="button" role="tab" className="dtab"
              aria-selected={item.key === tab} onClick={() => selectTab(item.key)}>
              {item.label}
            </button>
          ))}
        </div>
      )}

      {tab === 'stats' && (
        <div className="annual" aria-busy={stats.isPlaceholderData}
          style={stats.isPlaceholderData ? { opacity: 0.55, transition: 'opacity .2s' } : { transition: 'opacity .2s' }}>
          <p className="annual__note">{COHORT_NOTE}</p>

          {stats.isLoading && <Spinner label="載入統計" />}
          {stats.isError && <ErrorNotice error={stats.error} onRetry={() => void stats.refetch()} />}

          {stats.data && (
            <>
              <div className="annual__stats">
                <Stat label="新捐贈者" value={stats.data.newDonors} />
                <Stat label="新加入機構" value={stats.data.newOrganizations} />
                <Stat label="活躍捐贈者" value={stats.data.activeDonors} hint="期間內至少認領一次" />
                <Stat label="發布願望" value={stats.data.publishedWishes} />
                <Stat label="認領" value={stats.data.claimed} />
                <Stat label="完成" value={stats.data.completed}
                  hint={`完成率 ${Math.round(stats.data.completionRate * 100)}%`} />
              </div>

              <div className="annual__charts">
                <MonthlyPanel key={stats.data.period.key} scope="admin" title="每月認領趨勢"
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
      )}

      {tab === 'campaign' && campaign && <CampaignPanel campaign={campaign} onEdit={() => setEditing(campaign)} />}
      {tab === 'receipts' && campaign && <ReceiptsPanel campaign={campaign} />}

      <CampaignDialog campaign={editing} onClose={() => setEditing(null)} />
    </section>
  )
}
