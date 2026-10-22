import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Dialog } from '../../components/ui/Dialog'
import { api } from '../../lib/api'
import type { AdminWishDetail, WishFilterOptions } from '../../lib/types'

export interface ContentEditPayload {
  childAlias?: string
  ageRange?: string
  interests?: string
  description?: string
  reason: string
  freeze: boolean
}

/**
 * 管理員凍結願望的唯一入口：修改欄位是選填的（發現個資再改），原因必填。
 * 未凍結時一律凍結（機構看得到原因，可以修改後申請解除，認領者看到「暫停處理中」）；
 * 已凍結時只修改內容。禮物（願望標題）不能改——認領者可能已經照禮物買好了。
 * 歷史快照中被改的欄位會一併遮蔽，稽核只記欄位名稱。
 */
export function WishContentEditDialog({ open, wish, alreadyFrozen, onClose, onSubmit }: {
  open: boolean
  wish: AdminWishDetail
  alreadyFrozen: boolean
  onClose: () => void
  onSubmit: (payload: ContentEditPayload) => Promise<void>
}) {
  const [alias, setAlias] = useState(wish.childAlias ?? '')
  const [ageRange, setAgeRange] = useState('')
  const [description, setDescription] = useState(wish.description ?? '')
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const options = useQuery({
    queryKey: ['wish-options'],
    queryFn: () => api.get<WishFilterOptions>('/api/wishes/options'),
    enabled: open,
  })

  // 每次開啟以目前內容為起點
  const [lastOpen, setLastOpen] = useState(false)
  if (open !== lastOpen) {
    setLastOpen(open)
    if (open) {
      setAlias(wish.childAlias ?? ''); setAgeRange(''); setDescription(wish.description ?? '')
      setReason(''); setError(''); setBusy(false)
    }
  }

  async function submit() {
    const text = reason.trim()
    if (text.length < 4) { setError('請寫下處置原因（至少 4 個字）'); return }
    const freeze = !alreadyFrozen
    const payload: ContentEditPayload = { reason: text, freeze }
    if (alias.trim() !== (wish.childAlias ?? '')) payload.childAlias = alias.trim()
    if (ageRange) payload.ageRange = ageRange
    if (description.trim() !== (wish.description ?? '')) payload.description = description.trim()
    // 已凍結只剩「修改內容」，至少要改一個欄位；未凍結時欄位選填，不改也可以凍結
    if (!freeze && !payload.childAlias && !payload.ageRange && payload.description === undefined) {
      setError('沒有修改任何欄位')
      return
    }
    setBusy(true)
    try {
      await onSubmit(payload)
    } catch (e) {
      setError(e instanceof Error ? e.message : '送出失敗，請稍後再試')
      setBusy(false)
    }
  }

  return (
    <Dialog open={open} onClose={onClose} dismissible={!busy} labelledBy="wceTitle">
      <h2 id="wceTitle">{alreadyFrozen ? '修改內容' : '凍結這個願望'}</h2>
      <p>
        {alreadyFrozen
          ? '直接修改可能含個資的欄位。'
          : `凍結後，「${wish.title}」會立刻從願望牆消失；已認領的話，認領者看到「暫停處理中」、寄送期限暫停；機構看得到下面的原因，可以修改內容後申請解除。`}
        如果發現個資，可以順便改掉下面的欄位（選填）；禮物（願望標題）不能改，因為認領者可能已經買好了。
        歷史快照中被修改的欄位會一併遮蔽，認領者看到的是修改後的版本。
      </p>
      <div className="field" style={{ marginTop: 12 }}>
        <label className="field__label" htmlFor="wceAlias">孩子的暱稱</label>
        <input id="wceAlias" className="input" maxLength={50} value={alias}
          onChange={(event) => setAlias(event.target.value)} />
      </div>
      <div className="field" style={{ marginTop: 12 }}>
        <label className="field__label" htmlFor="wceAge">孩子的年齡（{wish.ageRangeLabel ?? '未填'}）</label>
        <select id="wceAge" className="input" value={ageRange} onChange={(event) => setAgeRange(event.target.value)}>
          <option value="">不修改</option>
          {options.data?.ageRanges.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
        </select>
      </div>
      <div className="field" style={{ marginTop: 12 }}>
        <label className="field__label" htmlFor="wceDesc">願望說明（20 字內）</label>
        <input id="wceDesc" className="input" maxLength={20} value={description}
          onChange={(event) => setDescription(event.target.value)} />
      </div>
      <div className={`field${error ? ' is-invalid' : ''}`} style={{ marginTop: 12 }}>
        <label className="field__label" htmlFor="wceReason">{alreadyFrozen ? '修改原因' : '凍結原因'}<span className="req" aria-hidden>*</span></label>
        <span className="field__hint">機構會看到這段原因（請勿重複外洩的內容），例如：信件照片露出孩子的真實姓名</span>
        <textarea id="wceReason" className="input" rows={3} value={reason}
          onChange={(event) => { setReason(event.target.value); setError('') }} />
        {error && <span className="field__error" role="alert">{error}</span>}
      </div>
      <div className="dialog__actions">
        <button type="button" className="btn" onClick={onClose} disabled={busy}>取消</button>
        <button type="button" className="btn btn--freeze-solid" onClick={() => void submit()} disabled={busy}>
          {busy ? '處理中…' : alreadyFrozen ? '儲存修改' : '凍結'}
        </button>
      </div>
    </Dialog>
  )
}
