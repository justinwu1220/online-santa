import {
  Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts'
import type { MonthlyCount } from '../../lib/types'

const MONTH_LABELS = [
  '1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月',
]

/**
 * 每月認領分布的長條圖。
 *
 * 單一數列（每月認領數），用品牌主色即可——顏色在這裡不承擔身分辨識，只是「有多少」
 * 的視覺化，不需要分類色盤（見 dataviz 色彩公式：單一數列不需要圖例框）。
 *
 * @param onMonthClick 給了就代表可以下鑽——點某個月的長條會回報該月份（1–12），
 *                      沒給就是純展示（例如已經在下鑽的每日檢視裡就不該再遞迴點）
 */
export function MonthlyClaimsChart({ data, onMonthClick }: {
  data: MonthlyCount[]
  onMonthClick?: (month: number) => void
}) {
  const chartData = data.map((entry) => ({ ...entry, label: MONTH_LABELS[entry.month - 1] }))

  return (
    <ResponsiveContainer width="100%" height={240}>
      <BarChart data={chartData} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
        <CartesianGrid vertical={false} stroke="#e1e0d9" />
        <XAxis dataKey="label" tickLine={false} axisLine={{ stroke: '#c3c2b7' }}
          tick={{ fill: '#898781', fontSize: 12 }} />
        <YAxis allowDecimals={false} tickLine={false} axisLine={false}
          tick={{ fill: '#898781', fontSize: 12 }} width={28} />
        <Tooltip
          cursor={{ fill: '#f0f9f4' }}
          contentStyle={{ borderRadius: 8, borderColor: '#dbf0e3', fontSize: 13 }}
          formatter={(value) => [`${value} 筆`, '認領數']}
        />
        <Bar
          dataKey="count"
          name="認領數"
          fill="#1f7a4c"
          radius={[4, 4, 0, 0]}
          maxBarSize={24}
          isAnimationActive={false}
          style={onMonthClick ? { cursor: 'pointer' } : undefined}
          onClick={onMonthClick
            ? (bar: { payload?: MonthlyCount }) => {
              if (bar.payload) onMonthClick(bar.payload.month)
            }
            : undefined}
        />
      </BarChart>
    </ResponsiveContainer>
  )
}
