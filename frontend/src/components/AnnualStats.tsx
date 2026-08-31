import { Select } from './Form'

/** 年度回顧頁共用的年份下拉——機構與監控中心的年度頁一字不差，值得共用。 */
export function YearSelect({ value, years, onChange }: {
  value: number
  years: number[]
  onChange: (year: number) => void
}) {
  return (
    <Select className="w-28" value={value}
      onChange={(event) => onChange(Number(event.target.value))}>
      {years.map((year) => <option key={year} value={year}>{year}</option>)}
    </Select>
  )
}

/** 年度回顧頁的統計卡，比照 AdminDashboard 的 Headline 但不可點擊——這裡沒有下一步可去。 */
export function AnnualStatCard({ label, value, hint }: {
  label: string
  value: number | string
  hint?: string
}) {
  return (
    <div className="rounded-lg bg-white p-4 ring-1 ring-slate-200">
      <p className="text-2xl font-bold tabular-nums text-slate-800">{value}</p>
      <p className="mt-0.5 text-sm font-medium text-slate-700">{label}</p>
      {hint && <p className="mt-0.5 text-xs text-slate-500">{hint}</p>}
    </div>
  )
}

/** cohort 制的說明，兩個年度頁共用同一段文字。 */
export function CohortNotice() {
  return (
    <p className="text-sm text-slate-500">
      數字以認領年度歸檔：年末認領、隔年才完成的禮物仍算在認領當年，
      進行中的認領完成後，數字將會更新。
    </p>
  )
}
