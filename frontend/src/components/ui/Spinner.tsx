import type { ReactNode } from 'react'
import { ApiError } from '../../lib/api'

/** 載入中。 */
export function Spinner({ label = '載入中' }: { label?: string }) {
  return (
    <div className="spinner" role="status">
      <i aria-hidden />
      <span>{label}…</span>
    </div>
  )
}

/** 請求失敗。顯示後端回的訊息，可選「重試」。 */
export function ErrorNotice({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const message = error instanceof ApiError
    ? error.detail
    : error instanceof Error ? error.message : '發生未預期的錯誤'

  return (
    <div className="notice notice--danger" role="alert">
      <p>{message}</p>
      {onRetry && (
        <p>
          <button type="button" className="btn btn--sm" onClick={onRetry}>重試</button>
        </p>
      )}
    </div>
  )
}

/** 沒有資料時的空白狀態。 */
export function EmptyBox({ icon, title, hint, children }: {
  icon?: string
  title: string
  hint?: string
  children?: ReactNode
}) {
  return (
    <div className="empty">
      {icon && <p aria-hidden style={{ fontSize: 32, margin: '0 0 8px' }}>{icon}</p>}
      <p style={{ margin: 0, fontWeight: 700, color: 'var(--fg-2)' }}>{title}</p>
      {hint && <p style={{ margin: '6px 0 0', fontSize: 14 }}>{hint}</p>}
      {children}
    </div>
  )
}
