import { useState } from 'react'
import { DCard } from '../../components/console/DrawerParts'
import { LazyBox } from '../../components/console/LazyBox'
import { ReasonDialog } from '../../components/console/ReasonDialog'
import { useToast } from '../../components/ui/toastContext'
import { api } from '../../lib/api'
import { formatDateTime } from '../../lib/format'
import type { SuspensionDetail } from '../../lib/types'
import { useInvalidateAdmin } from './adminQueries'

const FIELD_LABELS: Record<string, string> = {
  name: '機構名稱', contactPerson: '承辦人', responsiblePerson: '負責人', withholdingTaxId: '扣繳單位統一編號', contactEmail: '聯絡信箱', contactPhone: '聯絡電話',
  mobilePhone: '行動電話', address: '地址', preferredDeliveryTime: '方便收件時間', description: '簡介', website: '官網',
}

/**
 * 停權機構的處置資料（點擊載入，寫稽核）：停權原因、當下保全的原始資訊、停權期間機構修改了什麼、
 * 機構的解除申請，以及受影響的願望與認領數。可通知受影響的認領者、退回解除申請。
 * 核准解除申請就是「解除停權」（抽屜底部的按鈕）。
 */
export function SuspensionPanel({ orgId, orgName }: { orgId: string; orgName: string }) {
  const toast = useToast()
  const invalidate = useInvalidateAdmin()
  const [dialog, setDialog] = useState<'notify' | 'reject' | null>(null)

  return (
    <>
      <LazyBox key={`suspension-${orgId}`} title="停權處置資料" secret button="載入處置資料"
        desc="停權當下保全的原始資訊、停權期間機構的修改與解除申請。每次載入都會寫入稽核紀錄。"
        load={() => api.get<SuspensionDetail>(`/api/admin/organizations/${orgId}/suspension`)}>
        {(detail: SuspensionDetail) => (
          <>
            <DCard icon="alert" title="停權原因">
              <p className="dtext">{detail.reason ?? '—'}</p>
              {detail.suspendedAt && <p className="auth-note">停權於 {formatDateTime(detail.suspendedAt)}</p>}
              <p className="auth-note">
                影響 {detail.openWishes} 個公開中的願望、{detail.affectedClaims} 筆進行中的認領。
              </p>
              <button type="button" className="btn" onClick={() => setDialog('notify')}>通知受影響的認領者</button>
            </DCard>

            {detail.snapshot && (
              <DCard icon="building" title="停權當下的原始資訊">
                <ul className="changes">
                  {Object.entries(detail.snapshot)
                    .filter(([key]) => key !== 'documents')
                    .map(([key, value]) => (
                      <li key={key}><strong>{FIELD_LABELS[key] ?? key}</strong>：{String(value ?? '') || '—'}</li>
                    ))}
                  <li><strong>證明文件</strong>：{(detail.snapshot.documents ?? []).map((doc) => doc.fileName ?? '檔案').join('、') || '無'}</li>
                </ul>
              </DCard>
            )}

            <DCard icon="pencil" title={`停權期間的修改（${detail.revisions.length} 次）`}>
              {detail.revisions.length === 0
                ? <p className="auth-note">機構還沒有修改資料。</p>
                : detail.revisions.map((revision, index) => (
                  <div key={index} style={{ marginBottom: 8 }}>
                    <p className="auth-note">{formatDateTime(revision.at)}</p>
                    <ul className="changes">
                      {Object.entries(revision.changes).map(([field, change]) => (
                        <li key={field}>
                          <strong>{FIELD_LABELS[field] ?? field}</strong>：{change.before || '（空）'} → {change.after || '（空）'}
                        </li>
                      ))}
                    </ul>
                  </div>
                ))}
            </DCard>

            {detail.unlockRequest && (
              <DCard icon="pencil" title="機構的解除停權申請" className="dcard--key">
                <p className="dtext">{detail.unlockRequest.message || '（沒有附說明）'}</p>
                <p className="auth-note">
                  {{ PENDING: '等你審核', APPROVED: '已核准', REJECTED: '已退回', WITHDRAWN: '機構已撤回' }[detail.unlockRequest.status]}
                  {detail.unlockRequest.decisionNote && `：${detail.unlockRequest.decisionNote}`}
                </p>
                {detail.unlockRequest.status === 'PENDING' && (
                  <button type="button" className="btn btn--danger" onClick={() => setDialog('reject')}>退回申請</button>
                )}
              </DCard>
            )}
          </>
        )}
      </LazyBox>

      <ReasonDialog open={dialog === 'notify'} onClose={() => setDialog(null)} tone="freeze" rows={4}
        title="通知受影響的認領者？"
        body={`會寄 Email 給「${orgName}」所有進行中認領（含已寄出禮物）的認領者，並寫入稽核紀錄。`}
        label="要告訴認領者的話" hint="例如：該機構疑似冒名，請先不要再寄任何東西，我們正在處理" minLength={10}
        tooShort="請寫清楚要告訴認領者的話（至少 10 個字）" okText="寄出通知"
        onSubmit={async (message) => {
          const result = await api.post<{ notified: number }>(`/api/admin/organizations/${orgId}/notify-donors`, { note: message })
          toast.show(`已通知 ${result.notified} 位認領者`)
          setDialog(null)
        }} />
      <ReasonDialog open={dialog === 'reject'} onClose={() => setDialog(null)}
        title="退回解除停權申請？" body="機構維持停權，會在團隊對話看到你寫的原因，可以修改後再申請。"
        label="退回原因" hint="請具體說明還缺什麼" minLength={4} tooShort="請寫下退回原因（至少 4 個字）" okText="退回申請"
        onSubmit={async (reason) => {
          await api.post(`/api/admin/organizations/${orgId}/unlock-request/reject`, { note: reason })
          toast.show('已退回解除申請，機構會在團隊對話收到原因')
          await invalidate()
          setDialog(null)
        }} />
    </>
  )
}
