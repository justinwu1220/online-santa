import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api, withQuery } from '../../lib/api'
import { formatDate, formatDateTime } from '../../lib/format'
import type {
  OrganizationReviewView, OrganizationStatus, PageResponse,
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

function OrganizationCard({ organization }: { organization: OrganizationReviewView }) {
  const queryClient = useQueryClient()
  const [deciding, setDeciding] = useState<'approve' | 'reject' | null>(null)
  const [note, setNote] = useState('')

  const decide = useMutation({
    mutationFn: (decision: 'approve' | 'reject') =>
      api.post(`/api/admin/organizations/${organization.id}/${decision}`,
        note.trim() ? { note: note.trim() } : undefined),
    onSuccess: () => {
      setDeciding(null)
      setNote('')
      void queryClient.invalidateQueries({ queryKey: ['admin-organizations'] })
    },
  })

  const pending = organization.status === 'PENDING'

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

      {pending && (
        <div className="mt-4">
          {deciding ? (
            <form
              className="space-y-3"
              onSubmit={(event) => { event.preventDefault(); decide.mutate(deciding) }}
            >
              <Field
                label={deciding === 'approve' ? '核准附註' : '退件原因'}
                required={deciding === 'reject'}
                hint={deciding === 'reject' ? '機構看得到，請說明要補什麼' : undefined}
              >
                <TextArea rows={3} maxLength={1000} value={note}
                  required={deciding === 'reject'}
                  onChange={(event) => setNote(event.target.value)} />
              </Field>
              {decide.isError && <ErrorBanner error={decide.error} />}
              <div className="flex gap-2">
                <Button
                  type="submit"
                  variant={deciding === 'approve' ? 'primary' : 'danger'}
                  disabled={decide.isPending}
                >
                  {decide.isPending ? '處理中…' : deciding === 'approve' ? '確定核准' : '確定退件'}
                </Button>
                <Button variant="ghost" onClick={() => setDeciding(null)}>取消</Button>
              </div>
            </form>
          ) : (
            <div className="flex gap-2">
              <Button onClick={() => setDeciding('approve')}>核准</Button>
              <Button variant="secondary" onClick={() => setDeciding('reject')}>退件</Button>
            </div>
          )}
        </div>
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

