import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api, withQuery } from '../../lib/api'
import { formatDate, formatDateTime } from '../../lib/format'
import type {
  AttachmentView, OrganizationReviewView, OrganizationStatus, PageResponse,
} from '../../lib/types'
import { ConsolePanel } from '../../components/layouts/ConsoleLayout'
import { EmptyState, ErrorBanner, Spinner } from '../../components/Feedback'
import { Button, Field, Select, TextArea } from '../../components/Form'
import { Pagination } from '../../components/Pagination'
import { OrganizationStatusBadge } from '../../components/StatusBadge'

const STATUS_FILTERS: { value: OrganizationStatus | ''; label: string }[] = [
  { value: 'PENDING', label: '待審核' },
  { value: '', label: '全部' },
  { value: 'APPROVED', label: '已核准' },
  { value: 'REJECTED', label: '已退件' },
  { value: 'SUSPENDED', label: '已停權' },
]

export function AdminOrganizations() {
  const [status, setStatus] = useState<OrganizationStatus | ''>('PENDING')
  const [page, setPage] = useState(0)

  const organizations = useQuery({
    queryKey: ['admin-organizations', status, page],
    queryFn: () => api.get<PageResponse<OrganizationReviewView>>(
      withQuery('/api/admin/organizations', { status, page, size: 10 })),
  })

  return (
    <ConsolePanel
      title="機構審核"
      action={
        <Select className="w-32" value={status}
          onChange={(event) => {
            setStatus(event.target.value as OrganizationStatus | '')
            setPage(0)
          }}>
          {STATUS_FILTERS.map((option) => (
            <option key={option.value} value={option.value}>{option.label}</option>
          ))}
        </Select>
      }
    >
      <p className="mb-4 text-sm text-slate-600">
        機構上架的是孩童資料，核准前請確認對方確實是合法的兒少服務單位。
        審核決定會寫入稽核紀錄。
      </p>

      {organizations.isLoading && <Spinner label="載入機構" />}
      {organizations.isError && (
        <ErrorBanner error={organizations.error} onRetry={() => void organizations.refetch()} />
      )}

      {organizations.data?.content.length === 0 && (
        <EmptyState icon="✅" title="沒有符合條件的機構" />
      )}

      <div className="space-y-4">
        {organizations.data?.content.map((organization) => (
          <OrganizationCard key={organization.id} organization={organization} />
        ))}
      </div>

      {organizations.data && <Pagination page={organizations.data} onChange={setPage} />}
    </ConsolePanel>
  )
}

type Decision = 'approve' | 'reject' | 'suspend' | 'reactivate'

const DECISION_COPY: Record<Decision, { label: string; fieldLabel: string; required: boolean }> = {
  approve: { label: '核准附註', fieldLabel: '確定核准', required: false },
  reject: { label: '退件原因', fieldLabel: '確定退件', required: true },
  suspend: { label: '停權理由', fieldLabel: '確定停權', required: true },
  reactivate: { label: '復權附註', fieldLabel: '確定恢復', required: false },
}

function OrganizationCard({ organization }: { organization: OrganizationReviewView }) {
  const queryClient = useQueryClient()
  const [deciding, setDeciding] = useState<Decision | null>(null)
  const [note, setNote] = useState('')

  const decide = useMutation({
    mutationFn: (decision: Decision) =>
      api.post(`/api/admin/organizations/${organization.id}/${decision}`,
        note.trim() ? { note: note.trim() } : undefined),
    onSuccess: () => {
      setDeciding(null)
      setNote('')
      void queryClient.invalidateQueries({ queryKey: ['admin-organizations'] })
    },
  })

  const pending = organization.status === 'PENDING'
  const approved = organization.status === 'APPROVED'
  const suspended = organization.status === 'SUSPENDED'
  const destructive = deciding === 'reject' || deciding === 'suspend'

  return (
    <div className="rounded-lg border border-slate-200 p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="font-semibold text-slate-800">{organization.name}</h2>
          <p className="mt-0.5 text-sm text-slate-500">
            申請於 {formatDate(organization.createdAt)}
          </p>
        </div>
        <OrganizationStatusBadge status={organization.status} />
      </div>

      <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
        <Row label="承辦人" value={organization.contactPerson} />
        <Row label="聯絡信箱" value={organization.contactEmail} />
        <Row label="聯絡電話" value={organization.contactPhone} />
        <div className="sm:col-span-2">
          <Row label="地址" value={organization.address} />
        </div>
        {organization.description && (
          <div className="sm:col-span-2">
            <Row label="機構簡介" value={organization.description} />
          </div>
        )}
      </dl>

      {organization.reviewNote && (
        <p className="mt-4 rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-600">
          審核意見：{organization.reviewNote}
          {organization.reviewedAt && (
            <span className="ml-2 text-slate-400">
              （{formatDateTime(organization.reviewedAt)}）
            </span>
          )}
        </p>
      )}

      <DocumentsPanel organizationId={organization.id} />

      {(pending || approved || suspended) && (
        <div className="mt-4">
          {deciding ? (
            <form
              className="space-y-3"
              onSubmit={(event) => { event.preventDefault(); decide.mutate(deciding) }}
            >
              <Field
                label={DECISION_COPY[deciding].label}
                required={DECISION_COPY[deciding].required}
                hint={deciding === 'reject'
                  ? '機構看得到，請說明要補什麼'
                  : deciding === 'suspend'
                    ? '會記錄在稽核軌跡；機構目前看不到這則理由'
                    : undefined}
              >
                <TextArea rows={3} maxLength={1000} value={note}
                  required={DECISION_COPY[deciding].required}
                  onChange={(event) => setNote(event.target.value)} />
              </Field>
              {decide.isError && <ErrorBanner error={decide.error} />}
              <div className="flex gap-2">
                <Button
                  type="submit"
                  variant={destructive ? 'danger' : 'primary'}
                  disabled={decide.isPending}
                >
                  {decide.isPending ? '處理中…' : DECISION_COPY[deciding].fieldLabel}
                </Button>
                <Button variant="ghost" onClick={() => setDeciding(null)}>取消</Button>
              </div>
            </form>
          ) : (
            <div className="flex gap-2">
              {pending && (
                <>
                  <Button onClick={() => setDeciding('approve')}>核准</Button>
                  <Button variant="secondary" onClick={() => setDeciding('reject')}>退件</Button>
                </>
              )}
              {approved && (
                <Button variant="danger" onClick={() => setDeciding('suspend')}>停權</Button>
              )}
              {suspended && (
                <Button onClick={() => setDeciding('reactivate')}>恢復</Button>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  )
}

/**
 * 機構申請時附上的立案證明文件。按鈕才載入，跟認領的對話／附件一樣——
 * 這是對機構承辦人個資的一次具體存取，載入會另外寫入一筆稽核紀錄。
 */
function DocumentsPanel({ organizationId }: { organizationId: string }) {
  const documents = useQuery({
    queryKey: ['admin-organization', organizationId, 'documents'],
    queryFn: () => api.get<AttachmentView[]>(`/api/admin/organizations/${organizationId}/documents`),
    enabled: false,
  })

  return (
    <div className="mt-4">
      <div className="mb-2 flex items-center justify-between gap-3">
        <h3 className="text-sm font-medium text-slate-700">相關文件證明</h3>
        {!documents.isFetched && (
          <button
            type="button"
            onClick={() => void documents.refetch()}
            className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm font-medium
              text-slate-700 hover:bg-slate-50"
          >
            載入文件
          </button>
        )}
      </div>

      {!documents.isFetched && (
        <p className="text-sm text-slate-500">
          可能含法人登記證書等個資。<strong>載入會另外寫入一筆稽核紀錄。</strong>
        </p>
      )}

      {documents.isFetching && <Spinner label="載入文件" />}
      {documents.isError && <ErrorBanner error={documents.error} />}

      {documents.isFetched && !documents.isFetching && (
        (documents.data?.length ?? 0) === 0
          ? <p className="text-sm text-slate-500">這個機構沒有上傳任何文件。</p>
          : (
            <ul className="space-y-1.5">
              {documents.data?.map((doc) => (
                <li key={doc.id}>
                  <a href={doc.url} target="_blank" rel="noreferrer"
                    className="inline-flex items-center gap-1.5 text-sm text-santa-700
                      hover:underline">
                    {doc.contentType === 'application/pdf' ? '📄' : '🖼️'} 下載文件
                  </a>
                </li>
              ))}
            </ul>
          )
      )}
    </div>
  )
}

function Row({ label, value }: { label: string; value?: string }) {
  return (
    <div>
      <dt className="text-xs text-slate-500">{label}</dt>
      <dd className="mt-0.5 whitespace-pre-wrap text-slate-800">{value || '—'}</dd>
    </div>
  )
}

