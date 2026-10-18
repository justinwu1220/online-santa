import { useEffect, useState } from 'react'
import { describeAuthError, useAuth } from './authContext'

/** 重新寄送驗證信的冷卻秒數（SPEC 12.2：按下後 60 秒內不能重按）。 */
const COOLDOWN_SECONDS = 60

/**
 * 「重新寄送驗證信」：寄出後顯示倒數、冷卻期間不能重按。
 * 驗證提示列（VerifyBar）與註冊後的「驗證信寄出了」畫面共用。
 */
export function useResendVerification() {
  const auth = useAuth()
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [cooldownUntil, setCooldownUntil] = useState(0)
  const [now, setNow] = useState(() => Date.now())

  // 冷卻中每秒更新一次倒數
  useEffect(() => {
    if (cooldownUntil <= Date.now()) return
    const tick = window.setInterval(() => {
      setNow(Date.now())
      if (Date.now() >= cooldownUntil) window.clearInterval(tick)
    }, 1000)
    return () => window.clearInterval(tick)
  }, [cooldownUntil])

  const remaining = Math.max(0, Math.ceil((cooldownUntil - now) / 1000))

  async function resend() {
    setBusy(true)
    setMessage(null)
    setError(null)
    try {
      await auth.resendVerification()
      const until = Date.now() + COOLDOWN_SECONDS * 1000
      setNow(Date.now())
      setCooldownUntil(until)
      setMessage('驗證信已重新寄出')
    } catch (caught) {
      setError(describeAuthError(caught))
    } finally {
      setBusy(false)
    }
  }

  return { resend, busy, remaining, message, error, setMessage, setError }
}
