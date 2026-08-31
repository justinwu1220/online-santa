import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import type { DailyCount, MonthlyCount } from '../../lib/types'
import { ErrorBanner, Spinner } from '../Feedback'
import { Button } from '../Form'
import { ConsolePanel } from '../layouts/ConsoleLayout'
import { DailyClaimsChart } from './DailyClaimsChart'
import { MonthlyClaimsChart } from './MonthlyClaimsChart'

/**
 * 「每月分布」面板：預設顯示全年 12 個月，點某個月的長條下鑽成該月的每日分布，
 * 附返回鍵回到每月檢視。機構「年度回顧」與監控中心「年度營運」的下鑽邏輯完全一樣
 * ——差別只在資料來源與面板標題，因此抽成共用元件，避免兩頁各自維護一份
 * selectedMonth 的 state／effect／查詢邏輯。
 *
 * @param year 目前選定的年度。呼叫端要在外層元件加 `key={year}`——年度一變就讓這個
 *             元件整個重新掛載，下鑽狀態自然重設回每月檢視，不需要用 effect 去追蹤
 *             prop 變化再 setState（那會多一次無謂的 render）。跨年度的月下鑽沒有
 *             意義，且舊選的月份在新年度不見得有資料
 * @param title 未下鑽時的面板標題（兩頁文案不同：「每月認領分布」／「每月認領趨勢」）
 * @param monthlyData 全年 12 個月的資料，來自呼叫端已經取得的年度統計
 * @param fetchDaily 依 (year, month) 取得該月每日分布；只在下鑽時呼叫
 * @param queryScope 併入 queryKey 的呼叫端識別（機構頁傳 'org'、管理頁傳 'admin'）。
 *                   兩頁角色互斥、不會同時掛載，目前撞不到快取，但沒有這個字首的話
 *                   兩個頁面的下鑽查詢會共用同一把 queryKey，日後架構一變就是地雷
 */
export function MonthlyDrilldownPanel({ year, title, monthlyData, fetchDaily, queryScope }: {
  year: number
  title: string
  monthlyData: MonthlyCount[]
  fetchDaily: (year: number, month: number) => Promise<{ dailyClaims: DailyCount[] }>
  queryScope: string
}) {
  const [selectedMonth, setSelectedMonth] = useState<number | null>(null)

  const daily = useQuery({
    queryKey: [queryScope, 'annual-drilldown', year, selectedMonth],
    queryFn: () => fetchDaily(year, selectedMonth as number),
    enabled: selectedMonth !== null,
    placeholderData: keepPreviousData,
  })

  return (
    <ConsolePanel
      title={selectedMonth === null ? title : `${selectedMonth} 月認領分布`}
      action={selectedMonth !== null && (
        <Button variant="ghost" onClick={() => setSelectedMonth(null)}>← 返回每月</Button>
      )}
    >
      {selectedMonth === null ? (
        <>
          <MonthlyClaimsChart data={monthlyData} onMonthClick={setSelectedMonth} />
          <p className="mt-2 text-xs text-slate-400">點擊月份長條可查看單月分布</p>
        </>
      ) : (
        <>
          {daily.isLoading && <Spinner label="載入每日統計" />}
          {daily.isError && (
            <ErrorBanner error={daily.error} onRetry={() => void daily.refetch()} />
          )}
          {daily.data && <DailyClaimsChart data={daily.data.dailyClaims} />}
        </>
      )}
    </ConsolePanel>
  )
}
