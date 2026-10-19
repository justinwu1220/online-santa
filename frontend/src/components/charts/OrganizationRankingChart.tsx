import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { OrganizationCompletionRanking } from '../../lib/types'
import { CHART_PALETTE, tooltipStyle, type ChartTheme } from './palette'

/**
 * 機構完成認領排行的橫向長條圖，僅管理端可見。
 *
 * 單一數列，機構名稱只是分類軸上的類別，不需要分類色盤——長度本身就承擔了排序
 * 的資訊，顏色統一用品牌主色。
 */
export function OrganizationRankingChart({ data, theme = 'light' }: {
  data: OrganizationCompletionRanking[]
  theme?: ChartTheme
}) {
  const p = CHART_PALETTE[theme]
  if (data.length === 0) {
    return <p className="annual__empty">這個年度還沒有完成的認領</p>
  }

  return (
    <ResponsiveContainer width="100%" height={Math.max(160, data.length * 44)}>
      <BarChart data={data} layout="vertical" margin={{ top: 8, right: 24, left: 8, bottom: 0 }}>
        <CartesianGrid horizontal={false} stroke={p.grid} />
        <XAxis type="number" allowDecimals={false} tickLine={false}
          axisLine={{ stroke: p.axis }} tick={{ fill: p.tick, fontSize: 12 }} />
        <YAxis type="category" dataKey="organizationName" tickLine={false} axisLine={false}
          width={100} tick={{ fill: theme === 'dark' ? '#cbd5e1' : '#52514e', fontSize: 12 }} />
        <Tooltip
          cursor={{ fill: p.cursor }}
          contentStyle={tooltipStyle(theme)}
          formatter={(value) => [`${value} 筆`, '完成數']}
        />
        <Bar dataKey="completedCount" name="完成數" fill={p.bar} radius={[0, 4, 4, 0]}
          maxBarSize={24} isAnimationActive={false} />
      </BarChart>
    </ResponsiveContainer>
  )
}
