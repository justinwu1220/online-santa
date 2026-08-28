import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api, withQuery } from '../../lib/api'
import type { ClaimOrgView, PageResponse, WishOrgView } from '../../lib/types'
import { ConsolePanel } from '../../components/layouts/ConsoleLayout'
import { Spinner } from '../../components/Feedback'
import { useOrgContext } from './orgContext'

/**
 * 機構後台的首頁：只回答一個問題——「我現在有什麼要處理的？」
 *
 * 不放圖表。機構人員多半不是全職維護這個平台，進來就要能一眼看出待辦。
 */
export function OrgDashboard() {
  const { organization } = useOrgContext()

  const drafts = useCount('/api/organizations/me/wishes', { status: 'DRAFT' })
  const available = useCount('/api/organizations/me/wishes', { status: 'AVAILABLE' })
  const shipped = useCount('/api/organizations/me/claims', { status: 'SHIPPED' })
  const received = useCount('/api/organizations/me/claims', { status: 'RECEIVED' })
  const overdue = useCount('/api/organizations/me/claims/overdue', {})

  const loading = [drafts, available, shipped, received, overdue].some((q) => q.isLoading)
  if (loading) return <Spinner label="載入總覽" />

  return (
    <div className="space-y-5">
      <ConsolePanel title="需要你處理">
        <div className="grid gap-3 sm:grid-cols-3">
          <ActionCard
            to="/org/claims?status=SHIPPED"
            count={shipped.data ?? 0}
            label="待確認收到"
            hint="捐贈者已回報寄出，等你確認收到禮物"
            tone={shipped.data ? 'urgent' : 'calm'}
          />
          <ActionCard
            to="/org/claims?status=RECEIVED"
            count={received.data ?? 0}
            label="待回饋"
            hint="已收到禮物，可上傳回饋照片並結案"
            tone={received.data ? 'urgent' : 'calm'}
          />
          <ActionCard
            to="/org/overdue"
            count={overdue.data ?? 0}
            label="逾期未寄送"
            hint="超過期限仍未回報，建議先聯繫捐贈者"
            tone={overdue.data ? 'warning' : 'calm'}
          />
        </div>
      </ConsolePanel>

      <ConsolePanel title="願望狀況">
        <div className="grid gap-3 sm:grid-cols-2">
          <ActionCard
            to="/org/wishes?status=DRAFT"
            count={drafts.data ?? 0}
            label="草稿"
            hint={organization.canPublishWishes
              ? '尚未上架，捐贈者看不到'
              : '機構通過審核後即可上架'}
            tone="calm"
          />
          <ActionCard
            to="/org/wishes?status=AVAILABLE"
            count={available.data ?? 0}
            label="上架中"
            hint="正在願望牆上等待認領"
            tone="calm"
          />
        </div>
      </ConsolePanel>
    </div>
  )
}

/** 只取總數，size=1 讓後端不必回傳整頁資料。 */
function useCount(path: string, params: Record<string, string>) {
  return useQuery({
    queryKey: ['count', path, params],
    queryFn: async () => {
      const page = await api.get<PageResponse<WishOrgView | ClaimOrgView>>(
        withQuery(path, { ...params, size: 1 }))
      return page.totalElements
    },
  })
}

function ActionCard({ to, count, label, hint, tone }: {
  to: string
  count: number
  label: string
  hint: string
  tone: 'urgent' | 'warning' | 'calm'
}) {
  const countClass = {
    urgent: 'text-santa-600',
    warning: 'text-berry-600',
    calm: 'text-slate-400',
  }[tone]

  return (
    <Link
      to={to}
      className="rounded-lg border border-slate-200 p-4 transition-colors hover:bg-slate-50
        focus:outline-none focus:ring-2 focus:ring-santa-500"
    >
      <p className={`text-3xl font-bold tabular-nums ${countClass}`}>{count}</p>
      <p className="mt-1 font-medium text-slate-800">{label}</p>
      <p className="mt-0.5 text-sm text-slate-500">{hint}</p>
    </Link>
  )
}
