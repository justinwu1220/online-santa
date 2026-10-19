import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState, type ReactNode } from 'react'
import { Pager } from '../../components/ui/Pager'
import { ErrorNotice, Spinner } from '../../components/ui/Spinner'
import { api, withQuery } from '../../lib/api'
import { pageTitle } from '../../lib/brand'
import { formatDateTime } from '../../lib/format'
import type {
  AdminAuditAction, AttachmentCleanupResult, AuditLogView, DeadlineReminderResult,
  PageResponse, ReleaseSweepResult,
} from '../../lib/types'

const ACTION_LABELS: Record<AdminAuditAction, string> = {
  VIEW_CLAIM_DETAIL: '檢視認領詳情',
  VIEW_CLAIM_ATTACHMENTS: '檢視認領附件',
  VIEW_CLAIM_MESSAGES: '檢視認領對話',
  VIEW_CLAIM_RECEIPT: '檢視捐贈收據資料',
  APPROVE_ORGANIZATION: '核准機構',
  REJECT_ORGANIZATION: '退件',
  VIEW_ORGANIZATION_DOCUMENTS: '檢視機構文件',
  VIEW_ORGANIZATION_PHOTO: '檢視機構照片',
  SUSPEND_ORGANIZATION: '停權機構',
  REACTIVATE_ORGANIZATION: '恢復機構',
  APPROVE_WISH: '核准願望',
  REJECT_WISH: '退回願望',
  FREEZE_WISH: '凍結願望',
  UNFREEZE_WISH: '解除凍結',
  VIEW_WISH_ATTACHMENTS: '檢視願望附件',
  DELETE_ATTACHMENT: '刪除附件',
  DELETE_ORGANIZATION_DOCUMENT: '刪除機構文件',
  RUN_RELEASE_SWEEP: '執行逾期掃描',
  RUN_ATTACHMENT_CLEANUP: '執行附件清理',
  RUN_DEADLINE_REMINDERS: '執行期限提醒',
}

/** 存取個資或改變隱私敏感內容的動作要標出來，這樣掃過一眼就知道哪幾筆值得細看。 */
const SENSITIVE: AdminAuditAction[] = [
  'VIEW_CLAIM_DETAIL', 'VIEW_CLAIM_ATTACHMENTS', 'VIEW_CLAIM_MESSAGES', 'VIEW_CLAIM_RECEIPT',
  'VIEW_ORGANIZATION_DOCUMENTS', 'VIEW_ORGANIZATION_PHOTO', 'VIEW_WISH_ATTACHMENTS',
  'DELETE_ATTACHMENT', 'DELETE_ORGANIZATION_DOCUMENT',
]

const SCHEDULER_NOTE = '正式環境由 Cloud Scheduler 每日自動執行，走的是獨立的驗證鏈（Google OIDC token），與這裡的手動觸發是同一段邏輯。'

/**
 * 系統與稽核（設計稿 16.6）：沿用網站既有的功能，只換成新版外觀。
 * 功能、欄位與權限都不變：三個手動排程（逾期掃描、附件清理、期限提醒）與管理員操作紀錄。
 */
export function AdminSystem() {
  useEffect(() => {
    document.title = pageTitle('系統與稽核｜管理員後台')
    return () => { document.title = pageTitle() }
  }, [])

  return (
    <section className="org-tab" aria-labelledby="adSysTitle">
      <div className="org-head"><h1 id="adSysTitle">系統與稽核</h1></div>
      <JobPanel title="逾期認領掃描" button="立即執行" busyText="掃描中…" jobKey="release-expired-claims"
        desc={`${SCHEDULER_NOTE} 自動政策的機構會直接收回並讓願望重新上架，手動政策的只在該願望傳一則提醒訊息。`}
        result={(r: ReleaseSweepResult) => ({
          tone: 'ok',
          text: `掃到 ${r.overdueFound} 筆逾期：自動收回 ${r.autoReleased} 筆（${r.wishesReturnedToWall} 個願望回到願望牆），待機構自行處理 ${r.flaggedForOrganization} 筆。`,
        })} />
      <JobPanel title="PENDING 附件清理" button="立即執行" busyText="清理中…" jobKey="cleanup-pending-attachments"
        desc={`${SCHEDULER_NOTE} 清掉建立超過 24 小時仍未確認上傳的附件（放棄的上傳留下的孤兒紀錄），逐筆處理，單筆失敗不會擋住其他筆。`}
        result={(r: AttachmentCleanupResult) => ({
          tone: r.failed > 0 ? 'warn' : 'ok',
          text: `掃到 ${r.found} 筆超過 24 小時的孤兒附件：成功清除 ${r.deleted} 筆${r.failed > 0 ? `，${r.failed} 筆清除失敗（可再執行一次重試）` : ''}。`,
        })} />
      <JobPanel title="寄送期限提醒" button="立即執行" busyText="寄送中…" jobKey="send-deadline-reminders"
        desc={`${SCHEDULER_NOTE} 提醒寄送期限在 2 天內、還沒寄過提醒信的認領，逐筆處理，單筆失敗不會擋住其他筆。`}
        result={(r: DeadlineReminderResult) => ({
          tone: r.failed > 0 ? 'warn' : 'ok',
          text: `掃到 ${r.found} 筆快到期的認領：成功寄出 ${r.sent} 筆${r.failed > 0 ? `，${r.failed} 筆寄送失敗（可再執行一次重試）` : ''}。`,
        })} />
      <AuditPanel />
    </section>
  )
}

/** 一個手動排程的面板：說明、「立即執行」、執行結果（綠＝完成、黃＝有失敗）。 */
function JobPanel<T>({ title, desc, button, busyText, jobKey, result }: {
  title: string
  desc: ReactNode
  button: string
  busyText: string
  jobKey: string
  result: (data: T) => { tone: 'ok' | 'warn'; text: string }
}) {
  const queryClient = useQueryClient()
  const run = useMutation({
    mutationFn: () => api.post<T>(`/api/admin/jobs/${jobKey}`),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin-stats'] })
      void queryClient.invalidateQueries({ queryKey: ['audit-logs'] })
    },
  })
  const outcome = run.data ? result(run.data) : null

  return (
    <section className="panel set-sec">
      <div className="panel__head" style={{ marginBottom: 0 }}>
        <h2 className="panel__title">{title}</h2>
        <button type="button" className="btn btn--sm" disabled={run.isPending} onClick={() => run.mutate()}>
          {run.isPending ? busyText : button}
        </button>
      </div>
      <p className="set-sec__desc" style={{ margin: 0 }}>{desc}</p>
      {run.isError && <ErrorNotice error={run.error} />}
      {outcome && (
        <div className={`callout callout--${outcome.tone}`} role="status">
          <p>{outcome.text}</p>
        </div>
      )}
    </section>
  )
}

/**
 * 管理員操作紀錄。對所有管理員公開——有權限的人不該能安靜地做任何事；
 * 管理員之間互相看得到彼此的操作，這本身就是一種約束。
 */
function AuditPanel() {
  const [action, setAction] = useState<AdminAuditAction | ''>('')
  const [page, setPage] = useState(0)
  const logs = useQuery({
    queryKey: ['audit-logs', action, page],
    queryFn: () => api.get<PageResponse<AuditLogView>>(
      withQuery('/api/admin/audit-logs', { action, page, size: 30 })),
  })

  return (
    <section className="panel" aria-labelledby="adAuditTitle">
      <div className="panel__head" style={{ marginBottom: 6 }}>
        <h2 className="set-sec__title" id="adAuditTitle">管理員操作紀錄</h2>
        <select className="input" style={{ width: 'auto', minWidth: 160, marginLeft: 'auto' }} aria-label="依動作篩選"
          value={action}
          onChange={(event) => { setAction(event.target.value as AdminAuditAction | ''); setPage(0) }}>
          <option value="">全部動作</option>
          {Object.entries(ACTION_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
      </div>
      <p className="set-sec__desc" style={{ marginTop: 6 }}>
        管理員為了處理申訴需要跨機構檢視資料，其中包含捐贈者個資與可能有孩童影像的收件證明。
        在「願望」載入對話、附件或捐贈收據時，會各寫入一筆紀錄；這份紀錄對所有管理員公開。
      </p>

      {logs.isLoading && <Spinner label="載入紀錄" />}
      {logs.isError && <ErrorNotice error={logs.error} onRetry={() => void logs.refetch()} />}
      {logs.data && (
        <div className="table-wrap">
          <table className="audit">
            <thead>
              <tr><th scope="col">時間</th><th scope="col">操作者</th><th scope="col">動作</th><th scope="col">對象</th></tr>
            </thead>
            <tbody>
              {logs.data.content.length === 0
                ? <tr><td colSpan={4} style={{ color: 'var(--fg-muted)' }}>還沒有任何操作紀錄。</td></tr>
                : logs.data.content.map((log) => (
                  <tr key={log.id}>
                    <td>{formatDateTime(log.occurredAt)}</td>
                    <td>{log.adminEmail ?? '—'}</td>
                    <td style={SENSITIVE.includes(log.action) ? { color: '#fca5a5', fontWeight: 700 } : undefined}>
                      {ACTION_LABELS[log.action]}
                    </td>
                    <td>{log.detail ?? '—'}</td>
                  </tr>
                ))}
            </tbody>
          </table>
        </div>
      )}
      {logs.data && <Pager page={logs.data} onChange={setPage} />}
    </section>
  )
}
