import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../../lib/api'
import { formatDateTime } from '../../lib/format'
import type { AdminClaimView, AttachmentView, ClaimEventView } from '../../lib/types'
import { ConsolePanel } from '../../components/layouts/ConsoleLayout'
import { ErrorBanner, Notice, Spinner } from '../../components/Feedback'
import { Button } from '../../components/Form'
import { ClaimStatusBadge, OverdueBadge } from '../../components/StatusBadge'
import { Timeline } from '../../components/Timeline'

/**
 * 管理員檢視單筆認領。
 *
 * 這是全系統最敏感的畫面：捐贈者的姓名與 email、寄送證明、以及可能含孩童影像的
 * 回饋照片全都在這裡。因此**開啟這個頁面本身就會寫入稽核紀錄**——附件另外再記一筆。
 *
 * 畫面上直接告知使用者這件事。讓管理員知道自己的行為被記錄，本身就是一種約束。
 */
export function AdminClaimDetail() {
  const { id = '' } = useParams()

  const claim = useQuery({
    queryKey: ['admin-claim', id],
    queryFn: () => api.get<AdminClaimView>(`/api/admin/claims/${id}`),
  })

  const timeline = useQuery({
    queryKey: ['admin-claim', id, 'timeline'],
    queryFn: () => api.get<ClaimEventView[]>(`/api/admin/claims/${id}/timeline`),
  })

  if (claim.isLoading) return <Spinner label="載入認領" />
  if (claim.isError) {
    return <ErrorBanner error={claim.error} onRetry={() => void claim.refetch()} />
  }

  const data = claim.data!

  return (
    <div className="space-y-5">
      <Link to="/admin/claims" className="text-sm text-slate-500 hover:underline">
        ← 全站認領
      </Link>

      <Notice tone="warning">
        你正在檢視個人資料。這次存取已寫入稽核紀錄，其他管理員在「系統與稽核」看得到。
      </Notice>

      <ConsolePanel
        title={data.wishTitle}
        action={
          <span className="flex items-center gap-2">
            {data.overdue && <OverdueBadge />}
            <ClaimStatusBadge status={data.status} />
          </span>
        }
      >
        <dl className="grid gap-4 text-sm sm:grid-cols-2 lg:grid-cols-3">
          <Row label="機構" value={data.organizationName} />
          <Row label="孩子" value={data.childAlias} />
          <Row label="釋回政策快照" value={data.releasePolicySnapshot} />
          <Row label="捐贈者" value={data.donorName ?? '—'} />
          <Row label="捐贈者信箱" value={data.donorEmail} />
          <Row label="寄送期限" value={formatDateTime(data.shipDeadlineAt)} />
          <Row label="認領於" value={formatDateTime(data.claimedAt)} />
          <Row label="寄出於" value={formatDateTime(data.shippedAt)} />
          <Row label="機構收到於" value={formatDateTime(data.receivedAt)} />
          {data.trackingNumber && (
            <Row label="物流" value={`${data.trackingCarrier} ${data.trackingNumber}`} />
          )}
          {data.releaseReason && (
            <div className="sm:col-span-2 lg:col-span-3">
              <Row label="結束原因" value={data.releaseReason} />
            </div>
          )}
        </dl>
      </ConsolePanel>

      <div className="grid gap-5 lg:grid-cols-2">
        <ConsolePanel title="歷程">
          {timeline.isLoading
            ? <Spinner label="載入歷程" />
            : <Timeline events={timeline.data ?? []} />}
        </ConsolePanel>

        <AttachmentPanel claimId={id} />
      </div>
    </div>
  )
}

/**
 * 附件要按鈕才載入。
 *
 * 不自動抓的理由：看照片會另外寫一筆稽核，而管理員多半只是想看流程狀態。
 * 讓「看照片」成為一個明確的動作，而不是打開頁面的副作用。
 */
function AttachmentPanel({ claimId }: { claimId: string }) {
  const attachments = useQuery({
    queryKey: ['admin-claim', claimId, 'attachments'],
    queryFn: () => api.get<AttachmentView[]>(`/api/admin/claims/${claimId}/attachments`),
    enabled: false,
  })

  return (
    <ConsolePanel
      title="附件"
      action={
        !attachments.isFetched && (
          <button
            type="button"
            onClick={() => void attachments.refetch()}
            className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm font-medium
              text-slate-700 hover:bg-slate-50"
          >
            載入附件
          </button>
        )
      }
    >
      {!attachments.isFetched && (
        <p className="text-sm text-slate-500">
          寄送證明與回饋照片可能含捐贈者個資與孩童影像。
          <strong>載入會另外寫入一筆稽核紀錄。</strong>
        </p>
      )}

      {attachments.isFetching && <Spinner label="載入附件" />}
      {attachments.isError && <ErrorBanner error={attachments.error} />}

      {attachments.isFetched && !attachments.isFetching && (
        (attachments.data?.length ?? 0) === 0
          ? <p className="text-sm text-slate-500">這筆認領沒有任何附件。</p>
          : (
            <div className="grid grid-cols-3 gap-3">
              {attachments.data?.map((photo) => (
                <DeletablePhoto key={photo.id} photo={photo}
                  onDeleted={() => void attachments.refetch()} />
              ))}
            </div>
          )
      )}
    </ConsolePanel>
  )
}

/**
 * 管理員兩種附件（寄送證明與回饋照片）都能刪，用於隱私事件處置——
 * 例如誤傳的孩童照片。刪除會另外寫入 DELETE_ATTACHMENT 稽核紀錄（後端負責）。
 */
function DeletablePhoto({ photo, onDeleted }: { photo: AttachmentView; onDeleted: () => void }) {
  const [confirming, setConfirming] = useState(false)

  const remove = useMutation({
    mutationFn: () => api.delete(`/api/attachments/${photo.id}`),
    onSuccess: onDeleted,
  })

  return (
    <div className="group relative overflow-hidden rounded-lg ring-1 ring-slate-200">
      <a href={photo.url} target="_blank" rel="noreferrer">
        <img src={photo.url} alt=""
          className="aspect-square w-full object-cover transition-opacity group-hover:opacity-75" />
      </a>

      {confirming ? (
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-1.5
          bg-white/95 p-2 text-center">
          <p className="text-xs text-slate-600">刪除後無法復原</p>
          {remove.isError && <p className="text-xs text-berry-600">刪除失敗，請再試一次</p>}
          <div className="flex gap-1.5">
            <Button variant="danger" disabled={remove.isPending}
              onClick={() => remove.mutate()} className="px-2 py-1 text-xs">
              {remove.isPending ? '刪除中…' : '確定刪除'}
            </Button>
            <Button variant="ghost" onClick={() => setConfirming(false)} className="px-2 py-1 text-xs">
              取消
            </Button>
          </div>
        </div>
      ) : (
        <button type="button" onClick={() => setConfirming(true)}
          className="absolute right-1 top-1 rounded-md bg-white/90 px-1.5 py-0.5 text-xs
            text-slate-600 opacity-0 shadow transition-opacity hover:bg-berry-500
            hover:text-white group-hover:opacity-100">
          刪除
        </button>
      )}
    </div>
  )
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs text-slate-500">{label}</dt>
      <dd className="mt-0.5 text-slate-800">{value}</dd>
    </div>
  )
}
