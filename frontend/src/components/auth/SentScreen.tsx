import { useEffect, useRef } from 'react'
import { useAuth } from '../../lib/authContext'
import { burst, center, EASE_OUT, reducedMotion, rise } from '../../lib/motion'
import { useResendVerification } from '../../lib/useResendVerification'
import { VerifyCodeInput } from './VerifyCodeInput'

/**
 * 註冊成功後的「驗證信寄出了」畫面（依 prototype.html 的 sent 畫面與 playSent 動畫）。
 *
 * 信紙落進信封 → 封口闔上 → 信封輕輕浮起、撒星星 → 文字與按鈕依序浮上。
 * 信裡有一組 6 位數驗證碼，在這裡輸入；驗證成功才呼叫 `onVerified`。
 */
export function SentScreen({ onVerified }: { onVerified: () => void }) {
  const auth = useAuth()
  const resend = useResendVerification()
  const root = useRef<HTMLDivElement>(null)
  const envelope = useRef<SVGSVGElement>(null)
  const letter = useRef<SVGGElement>(null)
  const flap = useRef<SVGGElement>(null)

  // 進場動畫（playSent）。動畫都疊在預設可見的內容上，沒播也不會留下透明的東西
  useEffect(() => {
    if (reducedMotion()) return
    letter.current?.animate(
      [{ transform: 'translateY(-46px)' }, { transform: 'none' }],
      { duration: 560, delay: 120, easing: EASE_OUT, fill: 'backwards' })
    flap.current?.animate(
      [{ transform: 'scaleY(-1)' }, { transform: 'scaleY(1)' }],
      { duration: 380, delay: 620, easing: 'ease-in-out', fill: 'backwards' })
    envelope.current?.animate(
      [{ transform: 'none' }, { transform: 'translateY(-8px)' }, { transform: 'none' }],
      { duration: 700, delay: 1000, easing: 'ease-in-out' })
    const timer = window.setTimeout(() => {
      if (envelope.current?.isConnected) burst(...center(envelope.current), 16, 100)
    }, 1050)
    const items = root.current
      ? [...root.current.querySelectorAll('h2, p, .sent__actions')]
      : []
    rise(items, { delay: 300, stagger: 70, dist: 12, dur: 500 })
    return () => window.clearTimeout(timer)
  }, [])

  return (
    <div ref={root} className="sent">
      <svg ref={envelope} className="sent__env" width="150" height="150" viewBox="0 -34 120 124" aria-hidden="true">
        <rect x="5" y="20" width="110" height="65" rx="8" fill="#16234a" stroke="#fde68a" strokeWidth="1.6" />
        <g ref={letter} className="sent__letter">
          <rect x="18" y="26" width="84" height="54" rx="4" fill="#fbf8f1" />
          <path d="M28 38h50M28 47h64M28 56h40" stroke="#c9b99a" strokeWidth="2.4" strokeLinecap="round" />
        </g>
        <path d="M5 28 L60 60 L115 28 V77 Q115 85 107 85 H13 Q5 85 5 77 Z" fill="#1b2b58" stroke="#fde68a"
          strokeWidth="1.6" strokeLinejoin="round" />
        <g ref={flap} className="sent__flap">
          <path d="M5 28 L60 62 L115 28 Q115 20 107 20 H13 Q5 20 5 28 Z" fill="#22356a" stroke="#fde68a"
            strokeWidth="1.6" strokeLinejoin="round" />
          <circle cx="60" cy="56" r="7" fill="#f87171" />
          <path d="M60 51.5v9M55.5 56h9" stroke="#fff7ed" strokeWidth="1.6" strokeLinecap="round" />
        </g>
      </svg>
      <h2 id="sentTitle">驗證碼寄出了</h2>
      <p>
        我們寄了一封信到 <strong className="sent__email">{auth.email}</strong>，<br />
        請輸入信中的 6 位數驗證碼，就能開始認領願望。
      </p>
      <p className="auth-note auth-note--center">驗證碼 15 分鐘內有效。沒收到的話，記得看看垃圾郵件匣。</p>
      <VerifyCodeInput onVerified={onVerified} autoFocus />
      {(resend.message || resend.error) && (
        <p className={`auth-msg${resend.error ? ' auth-msg--danger' : ' auth-msg--ok'}`}
          role="status" style={{ marginTop: 14 }}>
          {resend.error ?? resend.message}
        </p>
      )}
      <div className="sent__actions">
        <button type="button" className="btn" disabled={resend.busy || resend.remaining > 0}
          onClick={() => void resend.resend()}>
          {resend.remaining > 0 ? `重新寄送驗證碼（${resend.remaining}）` : '重新寄送驗證碼'}
        </button>
      </div>
    </div>
  )
}
