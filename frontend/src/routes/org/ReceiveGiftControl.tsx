import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../../lib/api'
import type { ClaimOrgView } from '../../lib/types'
import { ErrorBanner } from '../../components/Feedback'
import { Button, Field, TextInput } from '../../components/Form'
import { ImageUploader } from '../../components/ImageUploader'

/** 台灣今天（YYYY-MM-DD），實際收到日期的預設值。 */
function todayInTaipei() {
  return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Taipei' }).format(new Date())
}

/**
 * 「確認收到並完成」按鈕與確認區塊，認領卡片與認領詳情共用。
 *
 * 一次走完收到與完成（設計稿 15.4）：上傳收件證明（必填）、選實際收到日期（預設今天）、
 * 勾「已拆封檢查，沒有禁止物品或可疑裝置」，才能送出。這是單向、不可逆的轉換，
 * 所以先開確認區塊。已寄出（SHIPPED）或舊流程留下的已收到（RECEIVED）才會畫東西。
 */
export function ReceiveGiftControl({ claim, onChanged }: {
  claim: ClaimOrgView; onChanged: () => void
}) {
  const [confirming, setConfirming] = useState(false)
  const [photos, setPhotos] = useState(0)
  const [receivedOn, setReceivedOn] = useState(todayInTaipei)
  const [inspected, setInspected] = useState(false)

  const complete = useMutation({
    mutationFn: () => api.post(`/api/organizations/me/claims/${claim.id}/complete`, {
      receivedOn: claim.status === 'SHIPPED' ? receivedOn : undefined,
      inspected,
    }),
    onSuccess: () => { setConfirming(false); onChanged() },
  })

  if (claim.status !== 'SHIPPED' && claim.status !== 'RECEIVED') return null

  if (!confirming) {
    return <Button onClick={() => setConfirming(true)}>確認收到並完成</Button>
  }

  return (
    <div className="w-full space-y-3 rounded-lg bg-slate-50 p-4">
      <p className="text-sm text-slate-600">
        確認收到並完成後，這筆認領就會結案，無法復原。
      </p>

      <Field label="收件證明照片" required hint="至少一張，讓認領者知道禮物平安送達">
        <div className="flex flex-wrap items-center gap-2">
          <ImageUploader purpose="ORG_FEEDBACK" targetId={claim.id}
            label={photos > 0 ? '再上傳一張' : '上傳收件證明'}
            onUploaded={() => setPhotos((count) => count + 1)} />
          {photos > 0 && <span className="text-sm text-slate-500">已上傳 {photos} 張</span>}
        </div>
      </Field>

      {claim.status === 'SHIPPED' && (
        <Field label="實際收到日期" required>
          <TextInput type="date" required max={todayInTaipei()} value={receivedOn}
            onChange={(event) => setReceivedOn(event.target.value)} />
        </Field>
      )}

      <label className="flex items-start gap-2 text-sm text-slate-700">
        <input type="checkbox" className="mt-1" checked={inspected}
          onChange={(event) => setInspected(event.target.checked)} />
        <span>已拆封檢查，沒有禁止物品或可疑裝置</span>
      </label>

      {complete.isError && <ErrorBanner error={complete.error} />}
      <div className="flex gap-2">
        <Button disabled={complete.isPending || !inspected} onClick={() => complete.mutate()}>
          {complete.isPending ? '處理中…' : '確認收到並完成'}
        </Button>
        <Button variant="ghost" onClick={() => setConfirming(false)}>取消</Button>
      </div>
    </div>
  )
}
