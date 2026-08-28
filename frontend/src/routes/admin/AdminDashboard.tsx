import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api } from '../../lib/api'
import {
  CLAIM_STATUS_LABELS, ORGANIZATION_STATUS_LABELS, WISH_STATUS_LABELS, formatDateTime,
} from '../../lib/format'
import type { PlatformStats } from '../../lib/types'
import { ConsolePanel } from '../../components/layouts/ConsoleLayout'
import { ErrorBanner, Spinner } from '../../components/Feedback'

const ROLE_LABELS: Record<string, string> = {
  DONOR: '一般民眾',
  ORG_MEMBER: '機構成員',
  ADMIN: '管理員',
}

export function AdminDashboard() {
  const stats = useQuery({
    queryKey: ['admin-stats'],
    queryFn: () => api.get<PlatformStats>('/api/admin/stats'),
    staleTime: 30_000,
  })

  if (stats.isLoading) return <Spinner label="載入統計" />
  if (stats.isError) {
    return <ErrorBanner error={stats.error} onRetry={() => void stats.refetch()} />
  }

  const data = stats.data!

  return (
    <div className="space-y-5">
      {/* 最上面只放三個真正需要行動的數字，其餘是分佈 */}
      <div className="grid gap-3 sm:grid-cols-3">
        <Headline
          to="/admin/organizations"
          value={data.pendingOrganizations}
          label="機構待審核"
          hint="核准後才能上架願望"
          urgent={data.pendingOrganizations > 0}
        />
        <Headline
          to="/admin/claims?overdue=true"
          value={data.overdueClaims}
          label="認領逾期未寄送"
          hint="孩子的願望正被卡著"
          urgent={data.overdueClaims > 0}
        />
        <Headline
          to="/admin/wishes?status=AVAILABLE"
          value={data.availableWishes}
          label="願望牆上可認領"
          hint="目前開放認領的數量"
          urgent={false}
        />
      </div>

      <div className="grid gap-5 lg:grid-cols-2">
        <ConsolePanel title="機構">
          <Distribution
            counts={data.organizations}
            labels={ORGANIZATION_STATUS_LABELS as Record<string, string>}
          />
        </ConsolePanel>

        <ConsolePanel title="使用者">
          <Distribution counts={data.users} labels={ROLE_LABELS} />
        </ConsolePanel>

        <ConsolePanel title="願望">
          <Distribution
            counts={data.wishes}
            labels={WISH_STATUS_LABELS as Record<string, string>}
          />
        </ConsolePanel>

        <ConsolePanel title="認領">
          <Distribution
            counts={data.claims}
            labels={CLAIM_STATUS_LABELS as Record<string, string>}
          />
        </ConsolePanel>
      </div>

      <p className="text-right text-xs text-slate-400">
        統計於 {formatDateTime(data.generatedAt)}
      </p>
    </div>
  )
}

function Headline({ to, value, label, hint, urgent }: {
  to: string; value: number; label: string; hint: string; urgent: boolean
}) {
  return (
    <Link
      to={to}
      className={`rounded-lg p-5 ring-1 transition-colors focus:outline-none focus:ring-2 ${
        urgent
          ? 'bg-white ring-berry-500/40 hover:bg-rose-50'
          : 'bg-white ring-slate-200 hover:bg-slate-50'
      }`}
    >
      <p className={`text-4xl font-bold tabular-nums ${
        urgent ? 'text-berry-600' : 'text-slate-700'
      }`}>
        {value}
      </p>
      <p className="mt-1 font-medium text-slate-800">{label}</p>
      <p className="mt-0.5 text-sm text-slate-500">{hint}</p>
    </Link>
  )
}

/**
 * 狀態分佈。
 *
 * 後端已把所有可能的狀態補齊（沒資料的補 0），所以這裡直接照順序畫，
 * 不必處理「這個狀態這次不見了」。長條用相對比例，不用固定刻度。
 */
function Distribution({ counts, labels }: {
  counts: Record<string, number>
  labels: Record<string, string>
}) {
  const entries = Object.entries(counts)
  const total = entries.reduce((sum, [, value]) => sum + value, 0)
  const max = Math.max(1, ...entries.map(([, value]) => value))

  if (total === 0) {
    return <p className="py-2 text-sm text-slate-400">目前沒有資料</p>
  }

  return (
    <div className="space-y-2">
      {entries.map(([key, value]) => (
        <div key={key} className="flex items-center gap-3">
          <span className="w-24 shrink-0 text-sm text-slate-600">{labels[key] ?? key}</span>
          <div className="h-2 flex-1 overflow-hidden rounded-full bg-slate-100">
            <div
              className="h-full rounded-full bg-santa-500"
              style={{ width: `${(value / max) * 100}%` }}
            />
          </div>
          <span className="w-12 shrink-0 text-right text-sm font-medium tabular-nums text-slate-800">
            {value}
          </span>
        </div>
      ))}
      <p className="pt-1 text-right text-xs text-slate-400">共 {total}</p>
    </div>
  )
}
