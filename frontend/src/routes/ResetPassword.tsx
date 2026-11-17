import { ArrowLeft, Eye, EyeOff } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Snowfall } from '../components/Snowfall'
import { FormField, Input } from '../components/ui/Fields'
import { ApiError } from '../lib/api'
import { describeAuthError } from '../lib/authContext'
import { PLATFORM_NAME, pageTitle } from '../lib/brand'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''
const MIN_PASSWORD = 8

/**
 * 重設密碼頁：信裡的連結落在這裡（`/reset-password?token=…`）。
 *
 * 在 PublicLayout 之外，和登入頁一樣是全螢幕版面——這時候使用者通常還沒登入，
 * 不該看到驗證提示列或需要登入的導覽。
 *
 * 成功之後所有裝置的登入狀態都被後端撤銷，所以這裡不自動登入，請使用者用新密碼登入。
 */
export function ResetPassword() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''

  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [passwordError, setPasswordError] = useState<string | null>(null)
  const [confirmError, setConfirmError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState(false)
  // 連結已失效（用過、過期、亂寫）：不必讓人白填一次新密碼
  const [dead, setDead] = useState(!token)

  useEffect(() => {
    document.title = pageTitle('重設密碼')
    return () => { document.title = pageTitle() }
  }, [])

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    const passwordMsg = !password ? '請設定新密碼'
      : password.length < MIN_PASSWORD ? `密碼至少要 ${MIN_PASSWORD} 個字元` : null
    const confirmMsg = !confirm ? '請再輸入一次新密碼'
      : password !== confirm ? '兩次輸入的密碼不一樣' : null
    setPasswordError(passwordMsg)
    setConfirmError(confirmMsg)
    if (passwordMsg || confirmMsg) return

    setBusy(true)
    setError(null)
    try {
      // 不走 api.ts：這支端點不需要 access token，也不該因為 401 去觸發換發
      const response = await fetch(`${API_BASE_URL}/api/auth/password/reset`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify({ token, newPassword: password }),
      })
      if (!response.ok) {
        const problem = await response.json().catch(() => undefined) as { detail?: string } | undefined
        throw new ApiError(response.status, problem?.detail ?? response.statusText, problem)
      }
      setDone(true)
    } catch (caught) {
      if ((caught as { errorCode?: string })?.errorCode === 'TOKEN_INVALID') setDead(true)
      else setError(describeAuthError(caught))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="rd">
      <Snowfall />
      <main className="page">
        <div className="auth">
          <div className="auth__brand">
            <span className="auth__title">
              <Link to="/" className="grad-text" style={{ textDecoration: 'none' }}>{PLATFORM_NAME}</Link>
            </span>
          </div>

          <div className="auth-card">
            <div className="auth-body">
              {done ? (
                <>
                  <div className="screen-head"><h2>密碼已重設</h2></div>
                  <p className="auth-msg auth-msg--ok">新密碼設定好了，其他裝置上的登入也都已登出。請用新密碼登入。</p>
                  <Link to="/login" className="btn btn--primary" style={{ display: 'block', textAlign: 'center' }}>
                    前往登入
                  </Link>
                </>
              ) : dead ? (
                <>
                  <div className="screen-head"><h2>這個連結已經失效</h2></div>
                  <p className="auth-msg auth-msg--danger" role="alert">
                    重設密碼的連結只能使用一次，而且 60 分鐘後就會過期。請重新申請一封新的。
                  </p>
                  <Link to="/login" className="btn btn--primary" style={{ display: 'block', textAlign: 'center' }}>
                    回到登入，重新申請
                  </Link>
                </>
              ) : (
                <>
                  <div className="screen-head"><h2>設定新密碼</h2></div>
                  <form className="auth-form" noValidate onSubmit={(event) => void submit(event)}>
                    <FormField label="新密碼" required hint={`至少 ${MIN_PASSWORD} 個字元`} error={passwordError}>
                      <span className="pw-wrap">
                        <Input type={showPassword ? 'text' : 'password'} autoComplete="new-password"
                          value={password} aria-invalid={Boolean(passwordError)}
                          onChange={(event) => { setPassword(event.target.value); setPasswordError(null) }} />
                        <button type="button" className="pw-toggle" onClick={() => setShowPassword((v) => !v)}
                          aria-label={showPassword ? '隱藏密碼' : '顯示密碼'}>
                          {showPassword ? <EyeOff className="icon" aria-hidden /> : <Eye className="icon" aria-hidden />}
                        </button>
                      </span>
                    </FormField>
                    <FormField label="確認新密碼" required error={confirmError}>
                      <Input type={showPassword ? 'text' : 'password'} autoComplete="new-password"
                        value={confirm} aria-invalid={Boolean(confirmError)}
                        onChange={(event) => { setConfirm(event.target.value); setConfirmError(null) }} />
                    </FormField>

                    {error && <p className="auth-msg auth-msg--danger" role="alert">{error}</p>}

                    <button type="submit" className="btn btn--primary" disabled={busy}>
                      {busy ? '處理中…' : '設定新密碼'}
                    </button>
                  </form>
                </>
              )}
            </div>
          </div>

          <Link to="/" className="auth__back">
            <ArrowLeft className="icon" aria-hidden />先看看有哪些願望
          </Link>
        </div>
      </main>
    </div>
  )
}
