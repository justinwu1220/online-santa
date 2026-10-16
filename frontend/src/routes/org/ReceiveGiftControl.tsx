import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../../lib/api'
import type { ClaimOrgView } from '../../lib/types'
import { ErrorBanner } from '../../components/Feedback'
import { Button } from '../../components/Form'

/**
 * 「確認收到禮物」按鈕與確認區塊，認領卡片與認領詳情共用。
 *
 * 這是單向、不可逆的狀態轉換（後端沒有任何「退回上一狀態」的端點），一按就送出的話
 * 誤觸沒辦法復原，所以先開確認區塊，跟「收回認領」「完成這筆送禮」同一套慣例。
 * 只在認領是 SHIPPED 時才畫東西。
 */
export function ReceiveGiftControl({ claim, onChanged }: {
  claim: ClaimOrgView; onChanged: () => void
}) {
  const [confirming, setConfirming] = useState(false)

  const receive = useMutation({
    mutationFn: () => api.post(`/api/organizations/me/claims/${claim.id}/receive`),
    onSuccess: () => { setConfirming(false); onChanged() },
  })

  if (claim.status !== 'SHIPPED') return null

  if (!confirming) {
    return <Button onClick={() => setConfirming(true)}>確認收到禮物</Button>
  }

  return (
    <div className="w-full rounded-lg bg-slate-50 p-4">
      <p className="text-sm text-slate-600">
        確定已經收到捐贈者寄來的禮物嗎？這個動作無法復原。
      </p>
      {receive.isError && <div className="mt-2"><ErrorBanner error={receive.error} /></div>}
      <div className="mt-3 flex gap-2">
        <Button disabled={receive.isPending} onClick={() => receive.mutate()}>
          {receive.isPending ? '處理中…' : '確定'}
        </Button>
        <Button variant="ghost" onClick={() => setConfirming(false)}>取消</Button>
      </div>
    </div>
  )
}
