import { useEffect, useRef, useState } from 'react'
import { useAuth, usingLocalAuth } from '../../lib/authContext'
import { EASE_OUT, pop, reducedMotion, wait } from '../../lib/motion'
import { useResendVerification } from '../../lib/useResendVerification'
import { VerifyCodeInput } from '../auth/VerifyCodeInput'

/** 認領按鈕被「尚未驗證信箱」擋下時，請提示列輕晃一下（設計稿 2723 行）。 */
export const SHAKE_VERIFY_BAR = 'online-santa:shake-verify-bar'

/**
 * 信箱驗證提示列：頁首正下方、全站顯示（SPEC 12.2，取代舊版頂端的黃色橫幅）。
 *
 * 信裡有一組 6 位數驗證碼，按「輸入驗證碼」展開輸入框，填滿就自動送出；驗證成功後
 * 不必重新登入，下一個請求就生效。
 * 「重新寄送」按下後 60 秒內顯示倒數、不能重按。
 *
 * 動畫（設計稿）：登入後才出現時高度由 0 展開（0.38s）；驗證完成變綠色「信箱驗證完成…」、
 * 圖示彈出，停 1.8 秒後高度收合（0.36s）；被擋下的認領按鈕會讓它輕晃一下。
 */
export function VerifyBar() {
  const auth = useAuth()
  const resendState = useResendVerification()
  const [entering, setEntering] = useState(false)
  const [gone, setGone] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  const signedIn = Boolean(auth.email)
  const verified = auth.emailVerified
  const done = signedIn && verified && !gone
  const needs = signedIn && !verified

  // 進入「完成」階段（false → true）才會走收合流程；一開始就已驗證的人完全看不到這條提示列。
  // 在 render 期間依狀態變化調整，不用 effect 設 state
  const [phase, setPhase] = useState<'idle' | 'done'>('idle')
  const [wasNeeds, setWasNeeds] = useState(needs)
  if (needs !== wasNeeds) {
    setWasNeeds(needs)
    if (needs) { setGone(false); setPhase('idle') } else if (signedIn) setPhase('done')
  }

  // 完成：圖示彈出 → 停 1.8 秒 → 高度收合 → 移除
  useEffect(() => {
    if (phase !== 'done') return
    let cancelled = false
    void (async () => {
      const el = ref.current
      if (el && !reducedMotion()) pop(el.querySelector('.bar__icon'), 0)
      await wait(1800)
      if (cancelled) return
      const node = ref.current
      if (node && !reducedMotion()) {
        const h = node.offsetHeight
        node.animate([{ height: `${h}px`, opacity: 1 }, { height: '0px', opacity: 0 }],
          { duration: 360, easing: 'ease-in', fill: 'forwards' })
        await wait(360)
      }
      if (!cancelled) setGone(true)
    })()
    return () => { cancelled = true }
  }, [phase])

  // 登入後才出現（不是重新整理）：高度由 0 展開
  const mounted = useRef(false)
  useEffect(() => {
    if (mounted.current && needs && ref.current && !reducedMotion()) {
      const h = ref.current.offsetHeight
      ref.current.animate([{ height: '0px', opacity: 0 }, { height: `${h}px`, opacity: 1 }],
        { duration: 380, easing: EASE_OUT })
    }
    mounted.current = true
  }, [needs])

  // 被擋下的認領按鈕呼叫：輕晃
  useEffect(() => {
    const shake = () => {
      if (reducedMotion()) return
      ref.current?.animate(
        [{ transform: 'translateX(0)' }, { transform: 'translateX(-6px)' }, { transform: 'translateX(6px)' }, { transform: 'none' }],
        { duration: 320 })
    }
    window.addEventListener(SHAKE_VERIFY_BAR, shake)
    return () => window.removeEventListener(SHAKE_VERIFY_BAR, shake)
  }, [])

  if (!signedIn || (!needs && !done) || (verified && phase !== 'done')) return null

  const remaining = resendState.remaining
  const shownError = resendState.error
  const shownMessage = resendState.message

  if (verified) {
    return (
      <div ref={ref} className="bar bar--verify is-ok" role="status" style={{ overflow: 'hidden' }}>
        <span className="bar__icon" aria-hidden>✓ </span>
        信箱驗證完成，現在可以認領願望或申請成為合作機構了。
      </div>
    )
  }

  return (
    <div ref={ref} className={`bar bar--verify${shownError ? ' is-error' : ''}`} role="status" style={{ overflow: 'hidden' }}>
      請先完成信箱驗證，才能認領願望或申請成為合作機構。我們寄了一組 6 位數驗證碼到 <strong>{auth.email}</strong>
      {usingLocalAuth && '，記得檢查垃圾郵件匣。'}
      <span className="bar__actions">
        <button type="button" className="btn btn--sm" aria-expanded={entering}
          onClick={() => setEntering((open) => !open)}>
          {entering ? '收起' : '輸入驗證碼'}
        </button>
        <button type="button" className="btn btn--sm btn--ghost"
          disabled={resendState.busy || remaining > 0}
          onClick={() => void resendState.resend()}>
          {remaining > 0 ? `重新寄送（${remaining} 秒）` : '重新寄送'}
        </button>
      </span>
      {entering && <VerifyCodeInput autoFocus />}
      {(shownMessage || shownError) && (
        <div style={{ marginTop: 4, fontSize: 12 }}>{shownError ?? shownMessage}</div>
      )}
    </div>
  )
}
