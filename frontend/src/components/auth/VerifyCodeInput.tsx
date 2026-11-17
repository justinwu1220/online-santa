import { useRef, useState } from 'react'
import { describeAuthError, useAuth } from '../../lib/authContext'

const LENGTH = 6

/**
 * 6 位數信箱驗證碼輸入。
 *
 * 一格一個數字，輸入後自動跳到下一格；可以整串貼上（也吃手機鍵盤的「自動填入驗證碼」）；
 * 填滿 6 位就自動送出，不必再按按鈕。送出失敗會清空重填，並顯示後端說的原因
 * （還剩幾次機會、已經過期、錯太多次要重寄）。
 *
 * 驗證成功後 `AuthProvider` 會把 `emailVerified` 變成 true，頁面其他部分自然跟著更新；
 * `onVerified` 給需要額外動作的呼叫端（例如註冊後的畫面要導向下一步）。
 */
export function VerifyCodeInput({ onVerified, autoFocus = false }: {
  onVerified?: () => void
  autoFocus?: boolean
}) {
  const auth = useAuth()
  const [digits, setDigits] = useState<string[]>(() => Array(LENGTH).fill(''))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const inputs = useRef<(HTMLInputElement | null)[]>([])

  function focusAt(index: number) {
    inputs.current[Math.max(0, Math.min(LENGTH - 1, index))]?.focus()
  }

  async function submit(code: string) {
    setBusy(true)
    setError(null)
    try {
      await auth.verifyEmail(code)
      onVerified?.()
    } catch (caught) {
      setError(describeAuthError(caught))
      setDigits(Array(LENGTH).fill(''))
      // 等輸入框解除 disabled 再聚焦，否則 focus 會被瀏覽器忽略
      window.setTimeout(() => focusAt(0), 0)
    } finally {
      setBusy(false)
    }
  }

  /** 從第 `start` 格起填入一串數字（單格輸入、整串貼上共用）。 */
  function fill(start: number, raw: string) {
    const typed = raw.replace(/\D/g, '').slice(0, LENGTH - start)
    if (!typed) return
    const next = [...digits]
    for (let i = 0; i < typed.length; i++) next[start + i] = typed[i]
    setDigits(next)
    setError(null)
    if (next.every(Boolean)) void submit(next.join(''))
    else focusAt(start + typed.length)
  }

  return (
    <div className="code-input">
      <div className="code-input__cells" role="group" aria-label="信箱驗證碼，共 6 位數字">
        {digits.map((digit, index) => (
          <input
            key={index}
            ref={(element) => { inputs.current[index] = element }}
            className="code-input__cell"
            type="text"
            inputMode="numeric"
            // 只有第一格標成 one-time-code：瀏覽器會把整串驗證碼塞進去，再由 fill 分到各格
            autoComplete={index === 0 ? 'one-time-code' : 'off'}
            maxLength={LENGTH}
            value={digit}
            disabled={busy}
            autoFocus={autoFocus && index === 0}
            aria-label={`第 ${index + 1} 位`}
            aria-invalid={Boolean(error)}
            onChange={(event) => {
              const value = event.target.value
              if (!value) {
                const next = [...digits]
                next[index] = ''
                setDigits(next)
              } else {
                fill(index, value)
              }
            }}
            onKeyDown={(event) => {
              if (event.key === 'Backspace' && !digit) {
                event.preventDefault()
                const next = [...digits]
                next[Math.max(0, index - 1)] = ''
                setDigits(next)
                focusAt(index - 1)
              } else if (event.key === 'ArrowLeft') {
                focusAt(index - 1)
              } else if (event.key === 'ArrowRight') {
                focusAt(index + 1)
              }
            }}
            onPaste={(event) => {
              event.preventDefault()
              fill(0, event.clipboardData.getData('text'))
            }}
            onFocus={(event) => event.target.select()}
          />
        ))}
      </div>
      {busy && <p className="auth-note auth-note--center" role="status">驗證中…</p>}
      {error && <p className="auth-msg auth-msg--danger" role="alert">{error}</p>}
    </div>
  )
}
