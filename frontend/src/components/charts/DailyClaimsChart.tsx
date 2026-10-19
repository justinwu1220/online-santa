import {
  Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts'
import type { DailyCount } from '../../lib/types'
import { CHART_PALETTE, tooltipStyle, type ChartTheme } from './palette'

/**
 * 單月每日認領分布的長條圖，點月份長條下鑽後顯示。
 *
 * 樣式刻意與 {@link MonthlyClaimsChart} 一致（同色、同 Tooltip 風格）——這是同一組
 * 資料的另一個時間顆粒度，換了樣式反而會讓人以為切到了不同的東西。
 */
export function DailyClaimsChart({ data, theme = 'light' }: { data: DailyCount[]; theme?: ChartTheme }) {
  const p = CHART_PALETTE[theme]
  const chartData = data.map((entry) => ({ ...entry, label: `${entry.day}日` }))

  return (
    <ResponsiveContainer width="100%" height={240}>
      <BarChart data={chartData} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
        <CartesianGrid vertical={false} stroke={p.grid} />
        <XAxis dataKey="label" tickLine={false} axisLine={{ stroke: p.axis }}
          interval="preserveStartEnd" tick={{ fill: p.tick, fontSize: 11 }} />
        <YAxis allowDecimals={false} tickLine={false} axisLine={false}
          tick={{ fill: p.tick, fontSize: 12 }} width={28} />
        <Tooltip
          cursor={{ fill: p.cursor }}
          contentStyle={tooltipStyle(theme)}
          formatter={(value) => [`${value} 筆`, '認領數']}
        />
        <Bar dataKey="count" name="認領數" fill={p.bar} radius={[4, 4, 0, 0]} maxBarSize={16}
          isAnimationActive={false} />
      </BarChart>
    </ResponsiveContainer>
  )
}
