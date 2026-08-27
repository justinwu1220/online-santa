import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api, withQuery } from '../../lib/api'
import { daysUntil, formatDate } from '../../lib/format'
import type { AttachmentView, ClaimOrgView, ClaimStatus, PageResponse } from '../../lib/types'
import { EmptyState, ErrorBanner, Notice, Spinner } from '../../components/Feedback'
import { Button, Field, Select, TextInput } from '../../components/Form'
import { ImageUploader } from '../../components/ImageUploader'
import { MessageThread } from '../../components/MessageThread'
import { Pagination } from '../../components/Pagination'
import { ClaimStatusBadge, OverdueBadge } from '../../components/StatusBadge'

const STATUS_FILTERS: { value: ClaimStatus | ''; label: string }[] = [
  { value: '', label: '全部' },
  { value: 'CLAIMED', label: '待寄送' },
  { value: 'SHIPPED', label: '運送中' },
  { value: 'RECEIVED', label: '已收到' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'RELEASED', label: '已收回' },
  { value: 'CANCELLED', label: '已取消' },
]

/** 機構的認領管理。`overdueOnly` 時改看逾期清單。 */
export function OrgClaims({ overdueOnly = false }: { overdueOnly?: boolean }) {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<ClaimStatus | ''>('')
  const [page, setPage] = useState(0)

  const path = overdueOnly
    ? '/api/organizations/me/claims/overdue'
    : '/api/organizations/me/claims'

  const claims = useQuery({
    queryKey: ['claims', 'org', overdueOnly, status, page],
    queryFn: () => api.get<PageResponse<ClaimOrgView>>(
      withQuery(path, { status: overdueOnly ? undefined : status, page, size: 10 })),
  })

  function refresh() {
    void queryClient.invalidateQueries({ queryKey: ['claims'] })
    void queryClient.invalidateQueries({ queryKey: ['org-wishes'] })
  }

  return (
    <div className="space-y-5">
      {overdueOnly ? (
        <Notice tone="warning">
          這些認領已超過寄送期限。建議先透過對話聯繫捐贈者；聯繫不上再收回，
          讓願望回到願望牆給其他人認領。
        </Notice>
      ) : (
        <Select className="w-40" value={status}
          onChange={(event) => { setStatus(event.target.value as ClaimStatus | ''); setPage(0) }}>
          {STATUS_FILTERS.map((option) => (
            <option key={option.value} value={option.value}>{option.label}</option>
          ))}
        </Select>
      )}

      {claims.isLoading && <Spinner label="載入認領" />}
      {claims.isError && <ErrorBanner error={claims.error} onRetry={() => void claims.refetch()} />}

      {claims.data?.content.length === 0 && (
        <EmptyState
          icon={overdueOnly ? '✅' : '📦'}
          title={overdueOnly ? '沒有逾期的認領' : '還沒有人認領'}
          hint={overdueOnly ? '所有認領都在期限內。' : '願望上架後就會出現在這裡。'}
        />
      )}

      <div className="space-y-4">
        {claims.data?.content.map((claim) => (
          <ClaimCard key={claim.id} claim={claim} onChanged={refresh} />
        ))}
      </div>

      {claims.data && <Pagination page={claims.data} onChange={setPage} />}
    </div>
  )
}

function ClaimCard({ claim, onChanged }: { claim: ClaimOrgView; onChanged: () => void }) {
  const queryClient = useQueryClient()
  const [expanded, setExpanded] = useState(false)
  const [releasing, setReleasing] = useState(false)
  const [reason, setReason] = useState('')

  const attachments = useQuery({
    queryKey: ['claim', claim.id, 'attachments'],
    queryFn: () => api.get<AttachmentView[]>(`/api/claims/${claim.id}/attachments`),
    enabled: expanded,
  })

  const advance = useMutation({
    mutationFn: (path: 'receive' | 'complete') =>
      api.post(`/api/organizations/me/claims/${claim.id}/${path}`),
    onSuccess: onChanged,
  })

  const release = useMutation({
    mutationFn: () => api.post(`/api/organizations/me/claims/${claim.id}/release`,
      reason.trim() ? { reason: reason.trim() } : undefined),
    onSuccess: () => { setReleasing(false); onChanged() },
  })

  const remaining = daysUntil(claim.shipDeadlineAt)
  const closed = ['COMPLETED', 'RELEASED', 'CANCELLED'].includes(claim.status)

  return (
    <div className="rounded-xl bg-white p-5 ring-1 ring-santa-100">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="font-semibold text-slate-800">{claim.wishTitle}</h3>
          <p className="mt-0.5 text-sm text-slate-500">
            給 {claim.childAlias}・認領者 {claim.donorName ?? claim.donorEmail}
          </p>
          <p className="text-sm text-slate-400">{claim.donorEmail}</p>
        </div>
        <div className="flex items-center gap-2">
          {claim.unreadMessageCount > 0 && (
            <span className="rounded-full bg-berry-500 px-2 py-0.5 text-xs font-medium text-white">
              {claim.unreadMessageCount} 則新訊息
            </span>
          )}
          {claim.overdue && <OverdueBadge />}
          <ClaimStatusBadge status={claim.status} />
        </div>
      </div>

      <p className="mt-3 text-sm text-slate-500">
        認領於 {formatDate(claim.claimedAt)}
        {claim.status === 'CLAIMED' && remaining !== null && (
          remaining >= 0
            ? `・還有 ${remaining} 天可寄送`
            : `・已逾期 ${Math.abs(remaining)} 天`
        )}
        {claim.trackingNumber && `・${claim.trackingCarrier} ${claim.trackingNumber}`}
      </p>

      {claim.donorMessage && (
        <p className="mt-3 rounded-lg bg-santa-50 px-3 py-2 text-sm text-slate-700">
          捐贈者留言：{claim.donorMessage}
        </p>
      )}

      <div className="mt-4 flex flex-wrap items-center gap-2">
        {claim.status === 'SHIPPED' && (
          <Button disabled={advance.isPending} onClick={() => advance.mutate('receive')}>
            確認收到禮物
          </Button>
        )}
        {claim.status === 'RECEIVED' && (
          <>
            <ImageUploader
              purpose="ORG_FEEDBACK"
              targetId={claim.id}
              label="上傳回饋照片"
              onUploaded={() =>
                queryClient.invalidateQueries({ queryKey: ['claim', claim.id, 'attachments'] })}
            />
            <Button disabled={advance.isPending} onClick={() => advance.mutate('complete')}>
              完成這筆送禮
            </Button>
          </>
        )}
        {claim.status === 'CLAIMED' && !releasing && (
          <Button variant="secondary" onClick={() => setReleasing(true)}>收回認領</Button>
        )}
        <Button variant="ghost" onClick={() => setExpanded((current) => !current)}>
          {expanded ? '收合' : '對話與照片'}
        </Button>
      </div>

      {advance.isError && <div className="mt-3"><ErrorBanner error={advance.error} /></div>}

      {releasing && (
        <form
          className="mt-4 space-y-3 rounded-lg bg-slate-50 p-4"
          onSubmit={(event) => { event.preventDefault(); release.mutate() }}
        >
          <Field label="收回原因" hint="會記錄在歷程中，捐贈者看得到">
            <TextInput maxLength={255} value={reason}
              placeholder="逾期未寄送，聯繫不上"
              onChange={(event) => setReason(event.target.value)} />
          </Field>
          {release.isError && <ErrorBanner error={release.error} />}
          <div className="flex gap-2">
            <Button variant="danger" type="submit" disabled={release.isPending}>
              {release.isPending ? '處理中…' : '確定收回'}
            </Button>
            <Button variant="ghost" onClick={() => setReleasing(false)}>取消</Button>
          </div>
          <p className="text-xs text-slate-500">收回後願望會立刻回到願望牆。</p>
        </form>
      )}

      {expanded && (
        <div className="mt-5 space-y-5 border-t border-santa-100 pt-5">
          <div>
            <h4 className="mb-2 text-sm font-medium text-slate-700">附件</h4>
            {attachments.isLoading ? <Spinner label="載入附件" /> : (
              (attachments.data?.length ?? 0) === 0
                ? <p className="text-sm text-slate-500">還沒有任何照片。</p>
                : (
                  <div className="grid grid-cols-4 gap-3">
                    {attachments.data?.map((photo) => (
                      <a key={photo.id} href={photo.url} target="_blank" rel="noreferrer"
                        className="overflow-hidden rounded-lg ring-1 ring-santa-100">
                        <img src={photo.url} alt="" className="aspect-square w-full object-cover" />
                      </a>
                    ))}
                  </div>
                )
            )}
          </div>

          <div>
            <h4 className="mb-2 text-sm font-medium text-slate-700">與捐贈者對話</h4>
            <MessageThread claimId={claim.id} closed={closed} />
          </div>
        </div>
      )}
    </div>
  )
}
