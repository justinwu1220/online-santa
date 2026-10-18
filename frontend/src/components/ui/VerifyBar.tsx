import { useState } from 'react'
import { describeAuthError, useAuth, usingFirebase } from '../../lib/authContext'
import { useResendVerification } from '../../lib/useResendVerification'

/**
 * 信箱驗證提示列：頁首正下方、全站顯示（SPEC 12.2，取代舊版頂端的黃色橫幅）。
 *
 * 使用者通常在另一個分頁點驗證信，這一頁不會自動知道，所以要有「我已經驗證好了」主動重新
 * 取得 token；沒有這個按鈕的話，驗證完回來還是被擋，只能登出再登入。
 * 「重新寄送」按下後 60 秒內顯示倒數、不能重按。
 */
export function VerifyBar() {
  const auth = useAuth()
  const resendState = useResendVerification()
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  if (!auth.email || auth.emailVerified) return null

  async function refresh() {
    setBusy(true)
    setMessage(null)
    setError(null)
    try {
      await auth.refreshVerification()
      setMessage('狀態已更新')
    } catch (caught) {
      setError(describeAuthError(caught))
    } finally {
      setBusy(false)
    }
  }

  const remaining = resendState.remaining
  const shownError = error ?? resendState.error
  const shownMessage = message ?? resendState.message

  return (
    <div className={`bar bar--verify${shownError ? ' is-error' : ''}`} role="status">
      請先完成信箱驗證，才能認領願望或申請成為合作機構。我們寄了一封信到 <strong>{auth.email}</strong>
      {usingFirebase && '，記得檢查垃圾郵件匣。'}
      <span className="bar__actions">
        <button type="button" className="btn btn--sm" disabled={busy}
          onClick={() => void refresh()}>
          我已經驗證好了
        </button>
        <button type="button" className="btn btn--sm btn--ghost"
          disabled={busy || resendState.busy || remaining > 0}
          onClick={() => void resendState.resend()}>
          {remaining > 0 ? `重新寄送（${remaining} 秒）` : '重新寄送'}
        </button>
      </span>
      {(shownMessage || shownError) && (
        <div style={{ marginTop: 4, fontSize: 12 }}>{shownError ?? shownMessage}</div>
      )}
    </div>
  )
}
