import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api, withQuery } from '../../lib/api'
import { formatDateTime } from '../../lib/format'
import type {
  AdminAuditAction, AuditLogView, PageResponse, ReleaseSweepResult,
} from '../../lib/types'
import { ConsolePanel } from '../../components/layouts/ConsoleLayout'
import { EmptyState, ErrorBanner, Notice, Spinner } from '../../components/Feedback'
import { Button, Select } from '../../components/Form'
import { Pagination } from '../../components/Pagination'

const ACTION_LABELS: Record<AdminAuditAction, string> = {
  VIEW_CLAIM_DETAIL: '檢視認領詳情',
  VIEW_CLAIM_ATTACHMENTS: '檢視認領附件',
  APPROVE_ORGANIZATION: '核准機構',
  REJECT_ORGANIZATION: '退件',
  RUN_RELEASE_SWEEP: '執行逾期掃描',
}

/** 存取個資的動作要標出來，這樣掃過一眼就知道哪幾筆值得細看。 */
const SENSITIVE: AdminAuditAction[] = ['VIEW_CLAIM_DETAIL', 'VIEW_CLAIM_ATTACHMENTS']

export function AdminSystem() {
  return (
    <div className="space-y-5">
      <ReleaseSweepPanel />
      <AuditTrailPanel />
    </div>
  )
}

function ReleaseSweepPanel() {
  const queryClient = useQueryClient()
  const sweep = useMutation({
    mutationFn: () => api.post<ReleaseSweepResult>('/api/admin/jobs/release-expired-claims'),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin-stats'] })
      void queryClient.invalidateQueries({ queryKey: ['audit-logs'] })
    },
  })

  return (
    <ConsolePanel
      title="逾期認領掃描"
      action={
        <Button variant="secondary" disabled={sweep.isPending} onClick={() => sweep.mutate()}>
          {sweep.isPending ? '掃描中…' : '立即執行'}
        </Button>
      }
    >
      <p className="text-sm text-slate-600">
        正式環境由 Cloud Scheduler 每日自動執行，走的是獨立的驗證鏈（Google OIDC token），
        與這裡的手動觸發是同一段邏輯。自動政策的機構會直接收回並讓願望重新上架，
        手動政策的只列入該機構的逾期提醒。
      </p>

      {sweep.isError && <div className="mt-3"><ErrorBanner error={sweep.error} /></div>}
      {sweep.data && (
        <div className="mt-3">
          <Notice tone="success">
            掃到 {sweep.data.overdueFound} 筆逾期：自動收回 {sweep.data.autoReleased} 筆
            （{sweep.data.wishesReturnedToWall} 個願望回到願望牆），
            待機構自行處理 {sweep.data.flaggedForOrganization} 筆。
          </Notice>
        </div>
      )}
    </ConsolePanel>
  )
}

/**
 * 稽核軌跡。
 *
 * 對所有管理員公開——有權限的人不該能安靜地做任何事。管理員之間互相看得到彼此的
 * 操作，這本身就是一種約束。
 */
function AuditTrailPanel() {
  const [action, setAction] = useState<AdminAuditAction | ''>('')
  const [page, setPage] = useState(0)

  const logs = useQuery({
    queryKey: ['audit-logs', action, page],
    queryFn: () => api.get<PageResponse<AuditLogView>>(
      withQuery('/api/admin/audit-logs', { action, page, size: 30 })),
  })

  return (
    <ConsolePanel
      title="管理員操作紀錄"
      action={
        <Select
          className="w-44"
          value={action}
          onChange={(event) => {
            setAction(event.target.value as AdminAuditAction | '')
            setPage(0)
          }}
        >
          <option value="">全部動作</option>
          {Object.entries(ACTION_LABELS).map(([value, label]) => (
            <option key={value} value={value}>{label}</option>
          ))}
        </Select>
      }
    >
      <p className="mb-4 text-sm text-slate-600">
        管理員為了處理申訴需要跨機構檢視資料，其中包含捐贈者個資與可能有孩童影像的
        回饋照片。這份紀錄對所有管理員公開。
      </p>

      {logs.isLoading && <Spinner label="載入紀錄" />}
      {logs.isError && <ErrorBanner error={logs.error} onRetry={() => void logs.refetch()} />}
      {logs.data?.content.length === 0 && (
        <EmptyState icon="📋" title="還沒有任何操作紀錄" />
      )}

      {(logs.data?.content.length ?? 0) > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="border-b border-slate-200 text-left text-xs text-slate-500">
              <tr>
                <th className="px-3 py-2 font-medium">時間</th>
                <th className="px-3 py-2 font-medium">管理員</th>
                <th className="px-3 py-2 font-medium">動作</th>
                <th className="px-3 py-2 font-medium">對象</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {logs.data?.content.map((log) => (
                <tr key={log.id} className={SENSITIVE.includes(log.action) ? 'bg-rose-50/50' : ''}>
                  <td className="whitespace-nowrap px-3 py-2.5 text-slate-500">
                    {formatDateTime(log.occurredAt)}
                  </td>
                  <td className="px-3 py-2.5 text-slate-700">{log.adminEmail ?? '—'}</td>
                  <td className="px-3 py-2.5">
                    <span className={SENSITIVE.includes(log.action)
                      ? 'font-medium text-berry-600'
                      : 'text-slate-700'}>
                      {ACTION_LABELS[log.action]}
                    </span>
                  </td>
                  <td className="px-3 py-2.5 text-slate-500">{log.detail ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {logs.data && <Pagination page={logs.data} onChange={setPage} />}
    </ConsolePanel>
  )
}
