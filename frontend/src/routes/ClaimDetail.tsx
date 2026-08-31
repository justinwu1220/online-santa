import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../lib/api'
import { daysUntil, formatDate } from '../lib/format'
import type { AttachmentView, ClaimDonorView, ClaimEventView } from '../lib/types'
import { ErrorBanner, Notice, Spinner } from '../components/Feedback'
import { Button, Field, TextInput } from '../components/Form'
import { ImageUploader } from '../components/ImageUploader'
import { MessageThread } from '../components/MessageThread'
import { ClaimStatusBadge, OverdueBadge } from '../components/StatusBadge'
import { Timeline } from '../components/Timeline'

const CLOSED_STATUSES = ['COMPLETED', 'RELEASED', 'CANCELLED']

export function ClaimDetail() {
  const { id = '' } = useParams()
  const queryClient = useQueryClient()

  const claim = useQuery({
    queryKey: ['claim', id],
    queryFn: () => api.get<ClaimDonorView>(`/api/claims/${id}`),
  })

  const timeline = useQuery({
    queryKey: ['claim', id, 'timeline'],
    queryFn: () => api.get<ClaimEventView[]>(`/api/claims/${id}/timeline`),
  })

  const attachments = useQuery({
    queryKey: ['claim', id, 'attachments'],
    queryFn: () => api.get<AttachmentView[]>(`/api/claims/${id}/attachments`),
  })

  function refreshAll() {
    void queryClient.invalidateQueries({ queryKey: ['claim', id] })
    void queryClient.invalidateQueries({ queryKey: ['claims'] })
  }

  if (claim.isLoading) return <Spinner label="載入認領" />
  if (claim.isError) {
    return <ErrorBanner error={claim.error} onRetry={() => void claim.refetch()} />
  }

  const data = claim.data!
  const closed = CLOSED_STATUSES.includes(data.status)
  const feedbackPhotos = attachments.data?.filter((a) => a.purpose === 'ORG_FEEDBACK') ?? []
  const shippingProofs = attachments.data?.filter((a) => a.purpose === 'SHIPPING_PROOF') ?? []

  return (
    <div className="space-y-8">
      <header>
        <Link to="/me/claims" className="text-sm text-slate-500 hover:underline">← 我的認領</Link>
        <div className="mt-2 flex flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="text-3xl font-bold text-santa-700">{data.wishTitle}</h1>
            <p className="mt-1 text-slate-600">
              給 {data.childAlias}・{data.organizationName}
            </p>
          </div>
          <div className="flex items-center gap-2">
            {data.overdue && <OverdueBadge />}
            <ClaimStatusBadge status={data.status} />
          </div>
        </div>
      </header>

      {data.status === 'CLAIMED' && <ShipDeadlineNotice deadline={data.shipDeadlineAt} />}
      {data.releaseReason && (
        <Notice tone="warning">這筆認領已結束：{data.releaseReason}</Notice>
      )}

      <div className="grid gap-8 lg:grid-cols-[1.3fr_1fr]">
        <div className="space-y-8">
          {/* 放在「回報寄送」之前：要先知道寄去哪，才談得上回報寄了沒 */}
          <ShippingAddressPanel claim={data} />

          {data.status === 'CLAIMED' && (
            <Panel title="回報寄送">
              <ShipForm claimId={id} onDone={refreshAll} />
            </Panel>
          )}

          {data.trackingNumber && (
            <Panel title="寄送資訊">
              <dl className="grid grid-cols-2 gap-4 text-sm">
                <div>
                  <dt className="text-xs text-slate-500">物流業者</dt>
                  <dd className="mt-0.5 font-medium">{data.trackingCarrier}</dd>
                </div>
                <div>
                  <dt className="text-xs text-slate-500">追蹤碼</dt>
                  <dd className="mt-0.5 font-medium">{data.trackingNumber}</dd>
                </div>
              </dl>
            </Panel>
          )}

          <Panel
            title="寄送證明"
            action={!closed && (
              <ImageUploader
                purpose="SHIPPING_PROOF"
                targetId={id}
                label="上傳照片"
                onUploaded={() => {
                  void queryClient.invalidateQueries({ queryKey: ['claim', id, 'attachments'] })
                }}
              />
            )}
          >
            <PhotoGrid photos={shippingProofs} emptyHint="上傳寄件單或包裹照片，讓機構安心。" />
          </Panel>

          {feedbackPhotos.length > 0 && (
            <Panel title="機構的回饋">
              <p className="mb-3 text-sm text-slate-500">
                這些照片只有你和機構看得到，網址會在數分鐘後失效。
              </p>
              <PhotoGrid photos={feedbackPhotos} emptyHint="" />
            </Panel>
          )}

          <Panel title="與機構對話">
            <MessageThread claimId={id} closed={closed} />
          </Panel>

          {!closed && data.status === 'CLAIMED' && (
            <Panel title="不方便繼續了？">
              <CancelForm claimId={id} onDone={refreshAll} />
            </Panel>
          )}
        </div>

        <Panel title="進度">
          {timeline.isLoading ? <Spinner label="載入歷程" /> : (
            <Timeline events={timeline.data ?? []} />
          )}
        </Panel>
      </div>
    </div>
  )
}

function Panel({ title, action, children }: {
  title: string; action?: React.ReactNode; children: React.ReactNode
}) {
  return (
    <section className="rounded-xl bg-white p-5 ring-1 ring-santa-100">
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 className="font-semibold text-slate-800">{title}</h2>
        {action}
      </div>
      {children}
    </section>
  )
}

function ShipDeadlineNotice({ deadline }: { deadline?: string }) {
  const remaining = daysUntil(deadline)
  if (remaining === null) return null

  return remaining >= 0 ? (
    <Notice>
      請於 <strong>{formatDate(deadline)}</strong> 前寄出禮物（還有 {remaining} 天）。
      寄出後記得回來回報，孩子才知道禮物在路上了。
    </Notice>
  ) : (
    <Notice tone="warning">
      已逾期 {Math.abs(remaining)} 天。機構可能會收回這個願望讓其他人認領——
      如果還想繼續，請盡快寄出並回報，或在下方與機構聯繫。
    </Notice>
  )
}

/**
 * 寄送地址。
 *
 * 這是捐贈者唯一看得到機構地址的地方——願望牆與願望詳情只有機構名稱。認領之後才需要
 * 知道寄去哪，那道界線由後端的擁有者檢查守著，不要把地址加進公開的願望視圖。
 */
function ShippingAddressPanel({ claim }: { claim: ClaimDonorView }) {
  const hasAddress = Boolean(claim.organizationAddress)

  return (
    <Panel title="寄送地址">
      {hasAddress ? (
        <dl className="space-y-3 text-sm">
          <div>
            <dt className="text-xs text-slate-500">收件單位</dt>
            <dd className="mt-0.5 font-medium text-slate-800">{claim.organizationName}</dd>
          </div>
          <div>
            <dt className="text-xs text-slate-500">地址</dt>
            <dd className="mt-0.5 font-medium text-slate-800">{claim.organizationAddress}</dd>
          </div>
          {claim.organizationPhone && (
            <div>
              <dt className="text-xs text-slate-500">聯絡電話</dt>
              <dd className="mt-0.5 font-medium text-slate-800">{claim.organizationPhone}</dd>
            </div>
          )}
        </dl>
      ) : (
        // 電話與地址在機構註冊時已是必填，但更早之前建立的機構可能沒有
        <Notice tone="warning">
          這個機構還沒有填寫收件地址，請用下方的訊息詢問機構。
        </Notice>
      )}
    </Panel>
  )
}

function ShipForm({ claimId, onDone }: { claimId: string; onDone: () => void }) {
  const [carrier, setCarrier] = useState('')
  const [trackingNumber, setTrackingNumber] = useState('')

  const ship = useMutation({
    mutationFn: () => api.post(`/api/claims/${claimId}/ship`, { carrier, trackingNumber }),
    onSuccess: onDone,
  })

  return (
    <form
      className="space-y-4"
      onSubmit={(event) => { event.preventDefault(); ship.mutate() }}
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="物流業者" required>
          <TextInput required maxLength={60} value={carrier}
            placeholder="黑貓宅急便、郵局…"
            onChange={(event) => setCarrier(event.target.value)} />
        </Field>
        <Field label="追蹤碼" required>
          <TextInput required maxLength={80} value={trackingNumber}
            onChange={(event) => setTrackingNumber(event.target.value)} />
        </Field>
      </div>
      {ship.isError && <ErrorBanner error={ship.error} />}
      <Button type="submit" disabled={ship.isPending}>
        {ship.isPending ? '送出中…' : '我已經寄出了'}
      </Button>
    </form>
  )
}

function CancelForm({ claimId, onDone }: { claimId: string; onDone: () => void }) {
  const [confirming, setConfirming] = useState(false)
  const [reason, setReason] = useState('')

  const cancel = useMutation({
    mutationFn: () => api.post(`/api/claims/${claimId}/cancel`,
      reason.trim() ? { reason: reason.trim() } : undefined),
    onSuccess: onDone,
  })

  if (!confirming) {
    return (
      <div>
        <p className="mb-3 text-sm text-slate-600">
          取消後這個願望會立刻回到願望牆，讓其他人有機會認領。
        </p>
        <Button variant="secondary" onClick={() => setConfirming(true)}>取消認領</Button>
      </div>
    )
  }

  return (
    <form className="space-y-3" onSubmit={(event) => { event.preventDefault(); cancel.mutate() }}>
      <Field label="取消原因" hint="會記錄在歷程中，機構看得到">
        <TextInput maxLength={255} value={reason}
          onChange={(event) => setReason(event.target.value)} />
      </Field>
      {cancel.isError && <ErrorBanner error={cancel.error} />}
      <div className="flex gap-2">
        <Button variant="danger" type="submit" disabled={cancel.isPending}>
          {cancel.isPending ? '處理中…' : '確定取消'}
        </Button>
        <Button variant="ghost" onClick={() => setConfirming(false)}>先不要</Button>
      </div>
    </form>
  )
}

function PhotoGrid({ photos, emptyHint }: { photos: AttachmentView[]; emptyHint: string }) {
  if (photos.length === 0) {
    return emptyHint ? <p className="text-sm text-slate-500">{emptyHint}</p> : null
  }

  return (
    <div className="grid grid-cols-3 gap-3">
      {photos.map((photo) => (
        <a key={photo.id} href={photo.url} target="_blank" rel="noreferrer"
          className="overflow-hidden rounded-lg ring-1 ring-santa-100">
          <img src={photo.url} alt="" className="aspect-square w-full object-cover" />
        </a>
      ))}
    </div>
  )
}
