import { useState } from 'react'
import { describeAuthError, useAuth, usingFirebase } from '../lib/authContext'

/**
 * 未驗證信箱的提示。
 *
 * 使用者通常在另一個分頁點驗證信，這一頁不會自動知道——所以要有「我已經驗證好了」
 * 按鈕主動重新取得 token。沒有這個按鈕的話，使用者驗證完回來還是被擋，
 * 只能靠登出再登入，體驗很糟。
 */
export function EmailVerificationBanner() {
  const auth = useAuth()
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  if (!auth.email || auth.emailVerified) return null

  async function run(action: () => Promise<void>, done?: string) {
    setBusy(true)
    setMessage(null)
    setError(null)
    try {
      await action()
      if (done) setMessage(done)
    } catch (caught) {
      setError(describeAuthError(caught))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="border-b border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900"
      role="status">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3">
        <span>
          請先完成信箱驗證，才能認領願望或申請成為合作機構。
          我們寄了一封信到 <strong>{auth.email}</strong>
          {usingFirebase && '，記得檢查垃圾郵件匣。'}
        </span>

        <span className="flex items-center gap-2">
          <button
            type="button"
            disabled={busy}
            onClick={() => void run(() => auth.refreshVerification(), '狀態已更新')}
            className="rounded-md bg-amber-900 px-3 py-1.5 text-xs font-medium text-white
              hover:bg-amber-800 disabled:opacity-50"
          >
            我已經驗證好了
          </button>
          <button
            type="button"
            disabled={busy}
            onClick={() => void run(() => auth.resendVerification(), '驗證信已重新寄出')}
            className="text-xs font-medium underline disabled:opacity-50"
          >
            重新寄送
          </button>
        </span>
      </div>

      {(message || error) && (
        <p className="mx-auto mt-2 max-w-6xl text-xs">
          {error ? <span className="text-berry-600">{error}</span> : message}
        </p>
      )}
    </div>
  )
}
