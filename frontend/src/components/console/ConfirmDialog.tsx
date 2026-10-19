import type { ReactNode } from 'react'
import { Dialog } from '../ui/Dialog'

/**
 * 一般確認框（設計稿「機構後台：一般確認」）：標題、說明、「再想想」與一個主要動作。
 * 刪除草稿等不可復原的動作用；`tone` 決定主要按鈕的顏色（預設紅色實心）。
 */
export function ConfirmDialog({ open, title, body, okText, tone = 'danger', busy, error, onOk, onClose, children }: {
  open: boolean
  title: string
  body: ReactNode
  okText: string
  tone?: 'danger' | 'primary' | 'tone'
  busy?: boolean
  error?: ReactNode
  onOk: () => void
  onClose: () => void
  /** 說明與按鈕之間的額外內容（例如收回原因） */
  children?: ReactNode
}) {
  const okClass = tone === 'danger' ? 'btn--danger-solid' : tone === 'tone' ? 'btn--tone-solid' : 'btn--primary'
  return (
    <Dialog open={open} onClose={onClose} dismissible={!busy} labelledBy="cf-title">
      <h2 id="cf-title">{title}</h2>
      <p>{body}</p>
      {children}
      {error && <p className="field__error" role="alert">{error}</p>}
      <div className="dialog__actions">
        <button type="button" className="btn" onClick={onClose} disabled={busy}>再想想</button>
        <button type="button" className={`btn ${okClass}`} onClick={onOk} disabled={busy}>
          {busy ? '處理中…' : okText}
        </button>
      </div>
    </Dialog>
  )
}
