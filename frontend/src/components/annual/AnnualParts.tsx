import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { DailyClaimsChart } from '../charts/DailyClaimsChart'
import { MonthlyClaimsChart } from '../charts/MonthlyClaimsChart'
import { SelectBox } from '../ui/Fields'
import { ErrorNotice, Spinner } from '../ui/Spinner'
import type { DailyCount, OperatingPeriod, PeriodMonthCount } from '../../lib/types'

/** 檔期回顧／年度營運共用的零件（機構與管理員兩頁一樣）。 */

export const COHORT_NOTE = '數字以認領所在的檔期歸檔：檔期結束後才完成的禮物仍算在認領當時的檔期，進行中的認領完成後，數字將會更新。'

/** YYYY-MM-DD → YYYY/M/D，期間選單與說明用，不經過時區換算。 */
export function shortDate(ymd: string) {
  const [year, month, day] = ymd.split('-').map(Number)
  return `${year}/${month}/${day}`
}

export function periodLabel(period: OperatingPeriod) {
  return period.unassigned
    ? `${period.name}（${shortDate(period.start)} 起）`
    : `${period.name}（${shortDate(period.start)} – ${shortDate(period.end)}）`
}

export function PeriodPicker({ value, periods, onChange }: {
  value: string
  periods: OperatingPeriod[]
  onChange: (key: string) => void
}) {
  return (
    <SelectBox className="annual__year" aria-label="檔期" value={value}
      onChange={(event) => onChange(event.target.value)}>
      {periods.map((item) => <option key={item.key} value={item.key}>{periodLabel(item)}</option>)}
    </SelectBox>
  )
}

export function Stat({ label, value, hint }: { label: string; value: number | string; hint?: string }) {
  return (
    <div className="stat">
      <p className="stat__value">{value}</p>
      <p className="stat__label">{label}</p>
      {hint && <p className="stat__hint">{hint}</p>}
    </div>
  )
}

/**
 * 每月認領分布：點某個月的長條下鑽成該月的每日分布。外層用 `key={期間 key}` 換期間時重新掛載，
 * 下鑽狀態自然回到每月。
 */
export function MonthlyPanel({ scope, title, monthly, fetchDaily }: {
  /** 併入 queryKey，機構頁與管理員頁的下鑽查詢不共用快取 */
  scope: string
  title: string
  monthly: PeriodMonthCount[]
  fetchDaily: (year: number, month: number) => Promise<{ dailyClaims: DailyCount[] }>
}) {
  const [picked, setPicked] = useState<{ year: number; month: number } | null>(null)
  const daily = useQuery({
    queryKey: [scope, 'period-drilldown', picked?.year, picked?.month],
    queryFn: () => fetchDaily(picked!.year, picked!.month),
    enabled: picked !== null,
    placeholderData: keepPreviousData,
  })
  const crossesYears = new Set(monthly.map((entry) => entry.year)).size > 1

  return (
    <section className="panel">
      <div className="panel__head">
        <h2 className="panel__title">
          {picked === null ? title : `${crossesYears ? `${picked.year} 年 ` : ''}${picked.month} 月認領分布`}
        </h2>
        {picked !== null && (
          <button type="button" className="btn btn--sm btn--ghost" onClick={() => setPicked(null)}>← 返回每月</button>
        )}
      </div>
      {picked === null ? (
        <>
          <MonthlyClaimsChart theme="dark" data={monthly}
            onMonthClick={(year, month) => setPicked({ year, month })} />
          <p className="annual__note">點擊月份長條可查看單月分布</p>
        </>
      ) : (
        <>
          {daily.isLoading && <Spinner label="載入每日統計" />}
          {daily.isError && <ErrorNotice error={daily.error} onRetry={() => void daily.refetch()} />}
          {daily.data && <DailyClaimsChart theme="dark" data={daily.data.dailyClaims} />}
        </>
      )}
    </section>
  )
}
