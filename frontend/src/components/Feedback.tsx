import type { ReactNode } from 'react'
import { ApiError } from '../lib/api'

export function Spinner({ label = '載入中' }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-2 py-12 text-slate-500" role="status">
      <span className="h-4 w-4 animate-spin rounded-full border-2 border-santa-500 border-t-transparent" />
      <span className="text-sm">{label}…</span>
    </div>
  )
}

export function EmptyState({ icon = '🎁', title, hint }: {
  icon?: string; title: string; hint?: ReactNode
}) {
  return (
    <div className="rounded-xl border border-dashed border-santa-100 bg-white/60 py-16 text-center">
      <p className="text-4xl">{icon}</p>
      <p className="mt-3 font-medium text-slate-700">{title}</p>
      {hint && <p className="mt-1 text-sm text-slate-500">{hint}</p>}
    </div>
  )
}

/**
 * 錯誤提示。
 *
 * 後端的每個錯誤都帶 errorCode 與可直接顯示的中文 detail，因此這裡不需要
 * 自己維護一份訊息對照表——顯示後端說的就好。
 */
export function ErrorBanner({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const message = error instanceof ApiError
    ? error.detail
    : error instanceof Error ? error.message : '發生未預期的錯誤'

  return (
    <div className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-berry-600" role="alert">
      <div className="flex items-start justify-between gap-4">
        <span>{message}</span>
        {onRetry && (
          <button type="button" onClick={onRetry}
            className="shrink-0 font-medium underline underline-offset-2">
            重試
          </button>
        )}
      </div>
    </div>
  )
}

export function Notice({ tone = 'info', children }: {
  tone?: 'info' | 'warning' | 'success'; children: ReactNode
}) {
  const classes = {
    info: 'border-santa-100 bg-santa-50 text-santa-700',
    warning: 'border-amber-200 bg-amber-50 text-amber-900',
    success: 'border-santa-100 bg-santa-100 text-santa-700',
  }[tone]

  return (
    <div className={`rounded-lg border px-4 py-3 text-sm ${classes}`}>{children}</div>
  )
}
