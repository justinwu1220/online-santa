import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { DailyClaimsChart } from '../charts/DailyClaimsChart'
import { MonthlyClaimsChart } from '../charts/MonthlyClaimsChart'
import { SelectBox } from '../ui/Fields'
import { ErrorNotice, Spinner } from '../ui/Spinner'
import type { DailyCount, MonthlyCount } from '../../lib/types'

/** 年度回顧／年度營運共用的零件（機構與管理員兩頁一樣）。 */

export const COHORT_NOTE = '數字以認領年度歸檔：年末認領、隔年才完成的禮物仍算在認領當年，進行中的認領完成後，數字將會更新。'

export function YearPicker({ value, years, onChange }: {
  value: number
  years: number[]
  onChange: (year: number) => void
}) {
  return (
    <SelectBox className="annual__year" aria-label="年度" value={value}
      onChange={(event) => onChange(Number(event.target.value))}>
      {years.map((item) => <option key={item} value={item}>{item}</option>)}
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
 * 每月認領分布：點某個月的長條下鑽成該月的每日分布。外層用 `key={year}` 換年度時重新掛載，
 * 下鑽狀態自然回到每月。
 */
export function MonthlyPanel({ scope, year, title, monthly, fetchDaily }: {
  /** 併入 queryKey，機構頁與管理員頁的下鑽查詢不共用快取 */
  scope: string
  year: number
  title: string
  monthly: MonthlyCount[]
  fetchDaily: (year: number, month: number) => Promise<{ dailyClaims: DailyCount[] }>
}) {
  const [month, setMonth] = useState<number | null>(null)
  const daily = useQuery({
    queryKey: [scope, 'annual-drilldown', year, month],
    queryFn: () => fetchDaily(year, month as number),
    enabled: month !== null,
    placeholderData: keepPreviousData,
  })

  return (
    <section className="panel">
      <div className="panel__head">
        <h2 className="panel__title">{month === null ? title : `${month} 月認領分布`}</h2>
        {month !== null && (
          <button type="button" className="btn btn--sm btn--ghost" onClick={() => setMonth(null)}>← 返回每月</button>
        )}
      </div>
      {month === null ? (
        <>
          <MonthlyClaimsChart theme="dark" data={monthly} onMonthClick={setMonth} />
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
