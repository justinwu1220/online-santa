import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { OrganizationCompletionRanking } from '../../lib/types'

/**
 * 機構完成認領排行的橫向長條圖，僅管理端可見。
 *
 * 單一數列，機構名稱只是分類軸上的類別，不需要分類色盤——長度本身就承擔了排序
 * 的資訊，顏色統一用品牌主色。
 */
export function OrganizationRankingChart({ data }: { data: OrganizationCompletionRanking[] }) {
  if (data.length === 0) {
    return <p className="py-16 text-center text-sm text-slate-400">這個年度還沒有完成的認領</p>
  }

  return (
    <ResponsiveContainer width="100%" height={Math.max(160, data.length * 44)}>
      <BarChart data={data} layout="vertical" margin={{ top: 8, right: 24, left: 8, bottom: 0 }}>
        <CartesianGrid horizontal={false} stroke="#e1e0d9" />
        <XAxis type="number" allowDecimals={false} tickLine={false}
          axisLine={{ stroke: '#c3c2b7' }} tick={{ fill: '#898781', fontSize: 12 }} />
        <YAxis type="category" dataKey="organizationName" tickLine={false} axisLine={false}
          width={100} tick={{ fill: '#52514e', fontSize: 12 }} />
        <Tooltip
          cursor={{ fill: '#f0f9f4' }}
          contentStyle={{ borderRadius: 8, borderColor: '#dbf0e3', fontSize: 13 }}
          formatter={(value) => [`${value} 筆`, '完成數']}
        />
        <Bar dataKey="completedCount" name="完成數" fill="#1f7a4c" radius={[0, 4, 4, 0]}
          maxBarSize={24} isAnimationActive={false} />
      </BarChart>
    </ResponsiveContainer>
  )
}
