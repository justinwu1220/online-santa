import { useState } from 'react'
import { describeAuthError, useAuth, usingFirebase } from '../../lib/authContext'

/**
 * 信箱驗證提示列：頁首正下方、全站顯示（SPEC 12.2，取代舊版頂端的黃色橫幅）。
 *
 * 使用者通常在另一個分頁點驗證信，這一頁不會自動知道，所以要有「我已經驗證好了」主動重新
 * 取得 token；沒有這個按鈕的話，驗證完回來還是被擋，只能登出再登入。
 * 「重新寄送」按下後 60 秒內顯示倒數、不能重按。
 */
export function VerifyBar() {
  const auth = useAuth()
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [cooldownUntil, setCooldownUntil] = useState(0)
  const [now, setNow] = useState(() => Date.now())

  if (!auth.email || auth.emailVerified) return null

  const remaining = Math.max(0, Math.ceil((cooldownUntil - now) / 1000))

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

  function resend() {
    void run(async () => {
      await auth.resendVerification()
      const until = Date.now() + 60_000
      setCooldownUntil(until)
      setNow(Date.now())
      const tick = window.setInterval(() => {
        setNow(Date.now())
        if (Date.now() >= until) window.clearInterval(tick)
      }, 1000)
    }, '驗證信已重新寄出')
  }

  return (
    <div className={`bar bar--verify${error ? ' is-error' : ''}`} role="status">
      請先完成信箱驗證，才能認領願望或申請成為合作機構。我們寄了一封信到 <strong>{auth.email}</strong>
      {usingFirebase && '，記得檢查垃圾郵件匣。'}
      <span className="bar__actions">
        <button type="button" className="btn btn--sm" disabled={busy}
          onClick={() => void run(() => auth.refreshVerification(), '狀態已更新')}>
          我已經驗證好了
        </button>
        <button type="button" className="btn btn--sm btn--ghost" disabled={busy || remaining > 0}
          onClick={resend}>
          {remaining > 0 ? `重新寄送（${remaining} 秒）` : '重新寄送'}
        </button>
      </span>
      {(message || error) && (
        <div style={{ marginTop: 4, fontSize: 12 }}>{error ?? message}</div>
      )}
    </div>
  )
}
