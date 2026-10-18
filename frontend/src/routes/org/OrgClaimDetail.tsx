import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { api } from '../../lib/api'
import { pendingStepsFor } from '../../lib/claimFlow'
import { daysUntil, formatDate, formatDateTime, formatPlainDate } from '../../lib/format'
import type { AttachmentView, ClaimEventView, ClaimOrgView } from '../../lib/types'
import { Breadcrumb } from '../../components/Breadcrumb'
import { ErrorBanner, Spinner } from '../../components/Feedback'
import { Button, Field, TextInput } from '../../components/Form'
import { ImageUploader } from '../../components/ImageUploader'
import { MessageThread } from '../../components/MessageThread'
import { ClaimStatusBadge, OverdueBadge } from '../../components/StatusBadge'
import { Timeline } from '../../components/Timeline'
import { AttachmentGroups } from './orgClaimAttachments'
import { ReceiveGiftControl } from './ReceiveGiftControl'

/**
 * 機構的單筆認領詳情。
 *
 * 認領管理清單只放摘要與「確認收到禮物」；完成、收回、回饋照片、與捐贈者的對話，
 * 以及捐贈者回報寄送時填的物流與禮物資訊，都在這一頁。
 */
export function OrgClaimDetail() {
  const { id = '' } = useParams()
  const queryClient = useQueryClient()

  const claim = useQuery({
    queryKey: ['claims', 'org', 'detail', id],
    queryFn: () => api.get<ClaimOrgView>(`/api/organizations/me/claims/${id}`),
  })

  const attachments = useQuery({
    queryKey: ['claim', id, 'attachments'],
    queryFn: () => api.get<AttachmentView[]>(`/api/claims/${id}/attachments`),
    enabled: claim.isSuccess,
  })

  // 歷程端點本來就允許「該筆認領的捐贈者與願望所屬機構」，別家機構一律 404
  const timeline = useQuery({
    queryKey: ['claim', id, 'timeline'],
    queryFn: () => api.get<ClaimEventView[]>(`/api/claims/${id}/timeline`),
    enabled: claim.isSuccess,
  })

  function refresh() {
    // 確認收到、完成、收回之後進度要立刻更新
    void queryClient.invalidateQueries({ queryKey: ['claim', id, 'timeline'] })
    void queryClient.invalidateQueries({ queryKey: ['claims'] })
    void queryClient.invalidateQueries({ queryKey: ['org-wishes'] })
  }

  const refreshAttachments = () =>
    queryClient.invalidateQueries({ queryKey: ['claim', id, 'attachments'] })

  const data = claim.data

  return (
    <div className="space-y-5">
      <Breadcrumb items={[
        { label: '認領管理', to: '/org/claims' },
        { label: data?.wishTitle ?? '認領詳情' },
      ]} />

      {claim.isLoading && <Spinner label="載入認領" />}
      {claim.isError && <ErrorBanner error={claim.error} onRetry={() => void claim.refetch()} />}

      {data && (
        <>
          <Header claim={data} />

          <Panel title="認領資料">
            <dl className="grid gap-4 text-sm sm:grid-cols-2">
              <Item label="認領者" value={data.donorName ?? '—'} />
              <Item label="認領者信箱" value={data.donorEmail} />
              <Item label="認領於" value={formatDate(data.claimedAt)} />
              {data.status === 'CLAIMED' && <ShipDeadline claim={data} />}
              {data.releaseReason && <Item label="結束原因" value={data.releaseReason} wide />}
            </dl>
            {data.donorMessage && (
              <p className="mt-4 rounded-lg bg-santa-50 px-3 py-2 text-sm text-slate-700">
                捐贈者留言：{data.donorMessage}
              </p>
            )}
          </Panel>

          <Panel title="進度">
            {timeline.isLoading ? <Spinner label="載入歷程" /> : (
              <Timeline events={timeline.data ?? []} pendingSteps={pendingStepsFor(data.status)} />
            )}
          </Panel>

          <Panel title="捐贈者回報的寄送資訊">
            {data.shippedAt ? (
              <dl className="grid gap-4 text-sm sm:grid-cols-2">
                <Item label="物流業者" value={data.trackingCarrier ?? '—'} />
                <Item label="追蹤碼" value={data.trackingNumber ?? '—'} />
                <Item label="實際禮物內容" value={data.giftDescription ?? '—'} wide />
                <Item label="數量"
                  value={data.giftQuantity != null ? `${data.giftQuantity} 份` : '—'} />
                <Item label="禮物價格"
                  value={data.giftPrice != null
                    ? `${data.giftPrice.toLocaleString('zh-TW')} 元` : '—'} />
                <Item label="寄出日期" value={formatPlainDate(data.shippedOn)} />
                <Item label="回報於" value={formatDateTime(data.shippedAt)} />
              </dl>
            ) : (
              <p className="text-sm text-slate-500">捐贈者還沒有回報寄送。</p>
            )}
          </Panel>

          <Actions claim={data} onChanged={refresh} onUploaded={refreshAttachments} />

          <Panel title="附件">
            {attachments.isLoading
              ? <Spinner label="載入附件" />
              : <AttachmentGroups attachments={attachments.data ?? []}
                  onDeleted={refreshAttachments} />}
          </Panel>

          <Panel title="與捐贈者對話">
            <MessageThread claimId={data.id}
              closed={['COMPLETED', 'RELEASED', 'CANCELLED'].includes(data.status)} />
          </Panel>
        </>
      )}
    </div>
  )
}

function Header({ claim }: { claim: ClaimOrgView }) {
  return (
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div>
        <h1 className="text-2xl font-bold text-slate-800">{claim.wishTitle}</h1>
        <p className="mt-1 text-sm text-slate-500">給 {claim.childAlias}</p>
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
  )
}

function ShipDeadline({ claim }: { claim: ClaimOrgView }) {
  const remaining = daysUntil(claim.shipDeadlineAt)
  return (
    <Item label="寄送期限"
      value={`${formatDate(claim.shipDeadlineAt)}${remaining === null ? '' : remaining >= 0
        ? `（還有 ${remaining} 天）` : `（已逾期 ${Math.abs(remaining)} 天）`}`} />
  )
}

/**
 * 這筆認領可以做的事。收到、完成、收回都是單向、不可逆的轉換，一律先確認。
 * 收到禮物的按鈕與確認區塊跟認領管理清單共用同一個元件。
 */
function Actions({ claim, onChanged, onUploaded }: {
  claim: ClaimOrgView; onChanged: () => void; onUploaded: () => void
}) {
  const [completing, setCompleting] = useState(false)
  const [releasing, setReleasing] = useState(false)
  const [reason, setReason] = useState('')

  const complete = useMutation({
    mutationFn: () => api.post(`/api/organizations/me/claims/${claim.id}/complete`),
    onSuccess: () => { setCompleting(false); onChanged() },
  })

  const release = useMutation({
    mutationFn: () => api.post(`/api/organizations/me/claims/${claim.id}/release`,
      reason.trim() ? { reason: reason.trim() } : undefined),
    onSuccess: () => { setReleasing(false); onChanged() },
  })

  const hasActions = ['CLAIMED', 'SHIPPED', 'RECEIVED'].includes(claim.status)
  if (!hasActions) return null

  return (
    <Panel title="處理這筆認領">
      <div className="flex flex-wrap items-center gap-2">
        <ReceiveGiftControl claim={claim} onChanged={onChanged} />

        {claim.status === 'RECEIVED' && (
          <>
            <ImageUploader purpose="ORG_FEEDBACK" targetId={claim.id} label="上傳收件證明"
              onUploaded={onUploaded} />
            {!completing && (
              <Button onClick={() => setCompleting(true)}>完成這筆送禮</Button>
            )}
          </>
        )}

        {claim.status === 'CLAIMED' && !releasing && (
          <Button variant="secondary" onClick={() => setReleasing(true)}>收回認領</Button>
        )}
      </div>

      {completing && (
        <div className="mt-4 rounded-lg bg-slate-50 p-4">
          <p className="text-sm text-slate-600">
            確定要完成這筆送禮嗎？完成後這筆認領就會結案，無法復原。
          </p>
          {complete.isError && <div className="mt-2"><ErrorBanner error={complete.error} /></div>}
          <div className="mt-3 flex gap-2">
            <Button disabled={complete.isPending} onClick={() => complete.mutate()}>
              {complete.isPending ? '處理中…' : '確定'}
            </Button>
            <Button variant="ghost" onClick={() => setCompleting(false)}>取消</Button>
          </div>
        </div>
      )}

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
    </Panel>
  )
}

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-xl bg-white p-5 ring-1 ring-santa-100">
      <h2 className="mb-3 text-sm font-semibold text-slate-700">{title}</h2>
      {children}
    </section>
  )
}

function Item({ label, value, wide }: { label: string; value: string; wide?: boolean }) {
  return (
    <div className={wide ? 'sm:col-span-2' : undefined}>
      <dt className="text-xs text-slate-400">{label}</dt>
      <dd className="mt-0.5 break-words font-medium text-slate-800">{value}</dd>
    </div>
  )
}
