import {
  Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts'
import type { DailyCount } from '../../lib/types'

/**
 * 單月每日認領分布的長條圖，點月份長條下鑽後顯示。
 *
 * 樣式刻意與 {@link MonthlyClaimsChart} 一致（同色、同 Tooltip 風格）——這是同一組
 * 資料的另一個時間顆粒度，換了樣式反而會讓人以為切到了不同的東西。
 */
export function DailyClaimsChart({ data }: { data: DailyCount[] }) {
  const chartData = data.map((entry) => ({ ...entry, label: `${entry.day}日` }))

  return (
    <ResponsiveContainer width="100%" height={240}>
      <BarChart data={chartData} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
        <CartesianGrid vertical={false} stroke="#e1e0d9" />
        <XAxis dataKey="label" tickLine={false} axisLine={{ stroke: '#c3c2b7' }}
          interval="preserveStartEnd" tick={{ fill: '#898781', fontSize: 11 }} />
        <YAxis allowDecimals={false} tickLine={false} axisLine={false}
          tick={{ fill: '#898781', fontSize: 12 }} width={28} />
        <Tooltip
          cursor={{ fill: '#f0f9f4' }}
          contentStyle={{ borderRadius: 8, borderColor: '#dbf0e3', fontSize: 13 }}
          formatter={(value) => [`${value} 筆`, '認領數']}
        />
        <Bar dataKey="count" name="認領數" fill="#1f7a4c" radius={[4, 4, 0, 0]} maxBarSize={16}
          isAnimationActive={false} />
      </BarChart>
    </ResponsiveContainer>
  )
}
