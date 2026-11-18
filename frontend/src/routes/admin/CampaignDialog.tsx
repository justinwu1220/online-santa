import { useState } from 'react'
import { Dialog } from '../../components/ui/Dialog'
import { FormField, Input, SelectBox } from '../../components/ui/Fields'
import type { CampaignRequest, CampaignStatus, CampaignView } from '../../lib/types'
import { useSaveCampaign } from './closingQueries'

const EMPTY: CampaignRequest = {
  name: '', activityNo: '', permitNo: '', approvedName: '',
  collectionStart: '', collectionEnd: '', useStart: '', useEnd: '', cutoffDate: '',
  receiptPrefix: '', firstReceiptSeq: undefined,
}

function fromCampaign(c: CampaignView): CampaignRequest {
  return {
    name: c.name, activityNo: c.activityNo ?? '', permitNo: c.permitNo ?? '', approvedName: c.approvedName ?? '',
    collectionStart: c.collectionStart, collectionEnd: c.collectionEnd, useStart: c.useStart, useEnd: c.useEnd,
    cutoffDate: c.cutoffDate ?? '', receiptPrefix: c.receiptPrefix, firstReceiptSeq: undefined, status: c.status,
  }
}

/**
 * 新增或修改活動檔期。
 *
 * 勸募許可文號核准前可以留空：留空時願望完成仍會開立收據（配號、存資料），但不會產生 PDF；
 * 填入後在收據頁按「補產 PDF」。已開立過收據後，收據前綴與起始號碼不能再改（編號要穩定）。
 */
export function CampaignDialog({ campaign, onClose }: {
  /** null＝關閉；'new'＝新增；檔期物件＝修改 */
  campaign: CampaignView | 'new' | null
  onClose: () => void
}) {
  const editing = campaign !== null && campaign !== 'new' ? campaign : null
  const [form, setForm] = useState<CampaignRequest>(EMPTY)
  const [error, setError] = useState('')
  const save = useSaveCampaign()

  // 每次開啟時載入對應內容（render 期間依 props 變化調整，不用 effect）
  const [lastKey, setLastKey] = useState<string | null>(null)
  const key = campaign === null ? null : campaign === 'new' ? 'new' : campaign.id + campaign.updatedAt
  if (key !== lastKey) {
    setLastKey(key)
    if (campaign !== null) {
      setForm(campaign === 'new' ? EMPTY : fromCampaign(campaign))
      setError('')
    }
  }

  const numberingLocked = editing !== null && editing.receiptsIssued > 0

  function set<K extends keyof CampaignRequest>(field: K, value: CampaignRequest[K]) {
    setForm((previous) => ({ ...previous, [field]: value }))
    setError('')
  }

  async function submit() {
    if (!form.name.trim()) { setError('請填寫檔期名稱'); return }
    if (!form.collectionStart || !form.collectionEnd || !form.useStart || !form.useEnd) {
      setError('請填寫勸募期間與財物使用期間'); return
    }
    const request: CampaignRequest = {
      ...form,
      name: form.name.trim(),
      cutoffDate: form.cutoffDate || undefined,
      receiptPrefix: form.receiptPrefix?.trim() || undefined,
      firstReceiptSeq: numberingLocked ? undefined : form.firstReceiptSeq || undefined,
    }
    try {
      await save.mutateAsync({ id: editing?.id, request })
      onClose()
    } catch (e) {
      setError(e instanceof Error ? e.message : '儲存失敗，請稍後再試')
    }
  }

  return (
    <Dialog open={campaign !== null} onClose={onClose} dismissible={!save.isPending} labelledBy="campaign-title">
      <h2 id="campaign-title">{editing ? '修改活動檔期' : '新增活動檔期'}</h2>
      <div className="form-grid" style={{ marginTop: 12 }}>
        <FormField label="檔期名稱" required span={6}>
          <Input value={form.name} maxLength={100} onChange={(e) => set('name', e.target.value)} />
        </FormField>
        <FormField label="活動編號" optional span={3} hint="主管機關系統的活動編號">
          <Input value={form.activityNo ?? ''} maxLength={30} onChange={(e) => set('activityNo', e.target.value)} />
        </FormField>
        <FormField label="勸募許可文號" optional span={3} hint="核准前可留空；留空時收據不產生 PDF">
          <Input value={form.permitNo ?? ''} maxLength={100} onChange={(e) => set('permitNo', e.target.value)} />
        </FormField>
        <FormField label="核准的活動全名" optional span={6} hint="印在收據上；沒填就用檔期名稱">
          <Input value={form.approvedName ?? ''} maxLength={200} onChange={(e) => set('approvedName', e.target.value)} />
        </FormField>
        <FormField label="勸募開始日" required span={3}>
          <Input type="date" value={form.collectionStart} onChange={(e) => set('collectionStart', e.target.value)} />
        </FormField>
        <FormField label="勸募結束日" required span={3}>
          <Input type="date" value={form.collectionEnd} onChange={(e) => set('collectionEnd', e.target.value)} />
        </FormField>
        <FormField label="財物使用開始日" required span={3}>
          <Input type="date" value={form.useStart} onChange={(e) => set('useStart', e.target.value)} />
        </FormField>
        <FormField label="財物使用結束日" required span={3}>
          <Input type="date" value={form.useEnd} onChange={(e) => set('useEnd', e.target.value)} />
        </FormField>
        <FormField label="統計截止日" optional span={3} hint="沒填就以勸募結束日為截止">
          <Input type="date" value={form.cutoffDate ?? ''} onChange={(e) => set('cutoffDate', e.target.value)} />
        </FormField>
        <FormField label="收據編號前綴" optional span={3}
          hint={numberingLocked ? '已開立收據，不能再改' : '大寫英數字，例如 OS2026；沒填用 OS＋年度'}>
          <Input value={form.receiptPrefix ?? ''} maxLength={20} disabled={numberingLocked}
            onChange={(e) => set('receiptPrefix', e.target.value.toUpperCase())} />
        </FormField>
        {!numberingLocked && (
          <FormField label="第一張收據的號碼" optional span={3} hint="預設從 1 開始">
            <Input type="number" min={1} max={999999} value={form.firstReceiptSeq ?? ''}
              onChange={(e) => set('firstReceiptSeq', e.target.value ? Number(e.target.value) : undefined)} />
          </FormField>
        )}
        {editing && (
          <FormField label="狀態" span={3} hint="結束的檔期不再開立新收據">
            <SelectBox value={form.status ?? 'ACTIVE'} onChange={(e) => set('status', e.target.value as CampaignStatus)}>
              <option value="ACTIVE">進行中</option>
              <option value="CLOSED">已結束</option>
            </SelectBox>
          </FormField>
        )}
      </div>
      {error && <p className="field__error" role="alert" style={{ marginTop: 12 }}>{error}</p>}
      <div className="dialog__actions">
        <button type="button" className="btn" onClick={onClose} disabled={save.isPending}>取消</button>
        <button type="button" className="btn btn--primary" onClick={() => void submit()} disabled={save.isPending}>
          {save.isPending ? '儲存中…' : '儲存'}
        </button>
      </div>
    </Dialog>
  )
}
