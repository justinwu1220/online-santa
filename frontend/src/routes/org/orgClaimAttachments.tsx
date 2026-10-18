import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../../lib/api'
import type { AttachmentView } from '../../lib/types'
import { Button } from '../../components/Form'

/**
 * 附件依上傳者分成兩組，不要混在同一格——寄送證明是認領人上傳的，回饋照片是
 * 機構自己上傳的，混在一起會看不出哪張是誰放的（比照捐贈者端 ClaimDetail.tsx
 * 的分組方式，WISH_IMAGE 不會出現在這個端點，篩選寫明確一點以防萬一）。
 */
export function AttachmentGroups({ attachments, onDeleted }: {
  attachments: AttachmentView[]; onDeleted: () => void
}) {
  const shippingProofs = attachments.filter((a) => a.purpose === 'SHIPPING_PROOF')
  const feedbackPhotos = attachments.filter((a) => a.purpose === 'ORG_FEEDBACK')

  if (shippingProofs.length === 0 && feedbackPhotos.length === 0) {
    return <p className="text-sm text-slate-500">還沒有任何照片。</p>
  }

  return (
    <div className="space-y-4">
      <AttachmentGroup title="寄送證明" photos={shippingProofs}
        emptyHint="認領人還沒上傳寄送證明。" />
      {/* 回饋照片是機構自己上傳的，才能刪；寄送證明是認領人上傳的，機構只能看 */}
      <AttachmentGroup title="收件證明" photos={feedbackPhotos}
        emptyHint="還沒有收件證明。" onDeleted={onDeleted} />
    </div>
  )
}

function AttachmentGroup({ title, photos, emptyHint, onDeleted }: {
  title: string; photos: AttachmentView[]; emptyHint: string; onDeleted?: () => void
}) {
  return (
    <div>
      <p className="mb-1.5 text-xs font-medium text-slate-500">{title}</p>
      {photos.length === 0 ? (
        <p className="text-sm text-slate-400">{emptyHint}</p>
      ) : (
        <div className="grid grid-cols-4 gap-3">
          {photos.map((photo) => (
            onDeleted
              ? <DeletablePhoto key={photo.id} photo={photo} onDeleted={onDeleted} />
              : <PhotoTile key={photo.id} photo={photo} />
          ))}
        </div>
      )}
    </div>
  )
}

function PhotoTile({ photo }: { photo: AttachmentView }) {
  return (
    <a href={photo.url} target="_blank" rel="noreferrer"
      className="overflow-hidden rounded-lg ring-1 ring-santa-100">
      <img src={photo.url} alt="" className="aspect-square w-full object-cover" />
    </a>
  )
}

function DeletablePhoto({ photo, onDeleted }: { photo: AttachmentView; onDeleted: () => void }) {
  const [confirming, setConfirming] = useState(false)

  const remove = useMutation({
    mutationFn: () => api.delete(`/api/attachments/${photo.id}`),
    onSuccess: onDeleted,
  })

  return (
    <div className="group relative overflow-hidden rounded-lg ring-1 ring-santa-100">
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
