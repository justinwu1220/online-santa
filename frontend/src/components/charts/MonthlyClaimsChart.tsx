import {
  Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts'
import type { PeriodMonthCount } from '../../lib/types'
import { CHART_PALETTE, tooltipStyle, type ChartTheme } from './palette'

/**
 * 每月認領分布的長條圖。
 *
 * 單一數列（每月認領數），用品牌主色即可——顏色在這裡不承擔身分辨識，只是「有多少」
 * 的視覺化，不需要分類色盤（見 dataviz 色彩公式：單一數列不需要圖例框）。
 *
 * 營運週期可能跨年，所以每筆資料帶年份；期間跨越兩個年份以上時標籤寫成「2026/11」，
 * 只在同一年內就寫「11月」。
 *
 * @param onMonthClick 給了就代表可以下鑽——點某個月的長條會回報該月份的年與月，
 *                      沒給就是純展示（例如已經在下鑽的每日檢視裡就不該再遞迴點）
 */
export function MonthlyClaimsChart({ data, onMonthClick, theme = 'light' }: {
  data: PeriodMonthCount[]
  theme?: ChartTheme
  onMonthClick?: (year: number, month: number) => void
}) {
  const p = CHART_PALETTE[theme]
  const spansYears = new Set(data.map((entry) => entry.year)).size > 1
  const chartData = data.map((entry) => ({
    ...entry, label: spansYears ? `${entry.year}/${entry.month}` : `${entry.month}月`,
  }))

  return (
    <ResponsiveContainer width="100%" height={240}>
      <BarChart data={chartData} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
        <CartesianGrid vertical={false} stroke={p.grid} />
        <XAxis dataKey="label" tickLine={false} axisLine={{ stroke: p.axis }}
          tick={{ fill: p.tick, fontSize: 12 }} />
        <YAxis allowDecimals={false} tickLine={false} axisLine={false}
          tick={{ fill: p.tick, fontSize: 12 }} width={28} />
        <Tooltip
          cursor={{ fill: p.cursor }}
          contentStyle={tooltipStyle(theme)}
          formatter={(value) => [`${value} 筆`, '認領數']}
        />
        <Bar
          dataKey="count"
          name="認領數"
          fill={p.bar}
          radius={[4, 4, 0, 0]}
          maxBarSize={24}
          isAnimationActive={false}
          style={onMonthClick ? { cursor: 'pointer' } : undefined}
          onClick={onMonthClick
            ? (bar: { payload?: PeriodMonthCount }) => {
              if (bar.payload) onMonthClick(bar.payload.year, bar.payload.month)
            }
            : undefined}
        />
      </BarChart>
    </ResponsiveContainer>
  )
}
