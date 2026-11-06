import { useState } from 'react'
import { Dialog } from '../../components/ui/Dialog'

/**
 * 解除凍結／核准解除申請的確認框。信件照片被屏蔽時多一個「恢復顯示信件照片」勾選（預設不勾）：
 * 不勾就維持屏蔽，願望回到願望牆但沒有信件，避免外洩的信件在解除凍結時又被看到。
 */
export function LetterRestoreDialog({ open, title, body, okText, letterMasked, busy, onConfirm, onClose }: {
  open: boolean
  title: string
  body: string
  okText: string
  /** 信件照片目前被屏蔽 */
  letterMasked: boolean
  busy: boolean
  onConfirm: (restoreLetter: boolean) => void
  onClose: () => void
}) {
  const [restore, setRestore] = useState(false)

  const [lastOpen, setLastOpen] = useState(false)
  if (open !== lastOpen) {
    setLastOpen(open)
    if (open) setRestore(false)
  }

  return (
    <Dialog open={open} onClose={onClose} dismissible={!busy} labelledBy="lrTitle">
      <h2 id="lrTitle">{title}</h2>
      <p>{body}</p>
      {letterMasked && (
        <label className="check-row">
          <input type="checkbox" checked={restore} onChange={(event) => setRestore(event.target.checked)} />
          <span>一併恢復顯示信件照片（信件已確認沒有個資再勾；不勾則願望回到牆上但沒有信件）</span>
        </label>
      )}
      <div className="dialog__actions">
        <button type="button" className="btn" onClick={onClose} disabled={busy}>取消</button>
        <button type="button" className="btn btn--tone-solid" onClick={() => onConfirm(restore)} disabled={busy}>
          {busy ? '處理中…' : okText}
        </button>
      </div>
    </Dialog>
  )
}
