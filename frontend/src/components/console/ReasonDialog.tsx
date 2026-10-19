import { useState } from 'react'
import { Dialog } from '../ui/Dialog'

/**
 * 要寫原因的確認框（設計稿「退回」「暫時凍結」「停權」）：標題、說明、必填原因、取消與主要動作。
 * 原因不夠長時欄位下方顯示紅字，不送出；送出失敗把後端的訊息顯示在原因欄下方。
 *
 * @param tone 主要按鈕：`danger` 紅色實心（退回），`freeze` 藍色實心（凍結、停權）
 */
export function ReasonDialog({ open, title, body, label, hint, minLength, tooShort, okText, tone = 'danger', rows = 4, onSubmit, onClose }: {
  open: boolean
  title: string
  body: string
  label: string
  hint?: string
  minLength: number
  tooShort: string
  okText: string
  tone?: 'danger' | 'freeze'
  rows?: number
  onSubmit: (reason: string) => Promise<void>
  onClose: () => void
}) {
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  // 每次開啟清空（render 期間依 props 變化調整，不用 effect）
  const [lastOpen, setLastOpen] = useState(false)
  if (open !== lastOpen) {
    setLastOpen(open)
    if (open) { setReason(''); setError(''); setBusy(false) }
  }

  async function submit() {
    const text = reason.trim()
    if (text.length < minLength) { setError(tooShort); return }
    setBusy(true)
    try {
      await onSubmit(text)
    } catch (e) {
      setError(e instanceof Error ? e.message : '送出失敗，請稍後再試')
      setBusy(false)
    }
  }

  return (
    <Dialog open={open} onClose={onClose} dismissible={!busy} labelledBy="rs-title">
      <h2 id="rs-title">{title}</h2>
      <p>{body}</p>
      <div className={`field${error ? ' is-invalid' : ''}`} style={{ marginTop: 12 }}>
        <label className="field__label" htmlFor="rsReason">{label}<span className="req" aria-hidden>*</span></label>
        {hint && <span className="field__hint">{hint}</span>}
        <textarea id="rsReason" className="input" rows={rows} value={reason}
          onChange={(event) => { setReason(event.target.value); setError('') }} />
        {error && <span className="field__error" role="alert">{error}</span>}
      </div>
      <div className="dialog__actions">
        <button type="button" className="btn" onClick={onClose} disabled={busy}>取消</button>
        <button type="button" className={`btn ${tone === 'freeze' ? 'btn--freeze-solid' : 'btn--danger-solid'}`}
          onClick={() => void submit()} disabled={busy}>{busy ? '處理中…' : okText}</button>
      </div>
    </Dialog>
  )
}
