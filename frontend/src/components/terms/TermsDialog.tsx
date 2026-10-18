import { X } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { formatDateTime } from '../../lib/format'
import { reducedMotion } from '../../lib/motion'
import { Dialog } from '../ui/Dialog'
import type { TermsDocument } from './termsContent'

/** 距離底部 12px 內就算讀到最下面（SPEC 13.1）。 */
const REACHED_TOLERANCE = 12

/**
 * 參與須知全文對話框（依 prototype.html 的 termsDialog）。
 *
 * - `consent`：註冊時、改版後重新同意時用。讀到最下面才能勾選，勾選後才能按同意。
 * - `readonly`：已同意的版本，給人再看一次。勾選框已勾不可動，不需要捲到底；有 `acceptedAt`
 *   就顯示「你已於…同意」。只有「關閉」。
 *
 * 可以開在另一個對話框之上（例如認領確認視窗）：Esc 與點背景只會關掉最上面這一個。
 * 放進 `.rd` 的 portal 而不是原地渲染——原地的話 `<dialog>` 會落在 `<p>` 裡，不合法的巢狀。
 */
export function TermsDialog({
  open, onClose, terms, mode = 'readonly', acceptedAt, acceptLabel = '同意', onAccept, outdated = false,
}: {
  open: boolean
  /** consent 模式下就是「取消」（含右上 ×、Esc） */
  onClose: () => void
  terms: TermsDocument
  mode?: 'consent' | 'readonly'
  acceptedAt?: string
  /** consent 模式的主要按鈕文字，例如「同意並註冊」 */
  acceptLabel?: string
  onAccept?: () => void | Promise<void>
  /** 前端的須知版本落後於後端：要求重新整理，不讓同意 */
  outdated?: boolean
}) {
  const consent = mode === 'consent'
  const doc = useRef<HTMLDivElement>(null)
  const checkbox = useRef<HTMLInputElement>(null)
  const [progress, setProgress] = useState(0)
  const [showTop, setShowTop] = useState(false)
  const [reached, setReached] = useState(!consent)
  const [checked, setChecked] = useState(!consent)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const measure = useCallback(() => {
    const element = doc.current
    if (!element) return
    const max = element.scrollHeight - element.clientHeight
    setProgress(max > 0 ? Math.min(100, Math.round(element.scrollTop / max * 100)) : 100)
    if (!consent) {
      setShowTop(element.scrollTop >= 200)
      return
    }
    // 內容短到不用捲（max <= 0）也算讀完，否則永遠勾不了
    if (max <= 0 || element.scrollTop + element.clientHeight >= element.scrollHeight - REACHED_TOLERANCE) {
      setReached((already) => {
        if (!already && checkbox.current && !reducedMotion()) {
          checkbox.current.animate(
            [{ transform: 'scale(1)' }, { transform: 'scale(1.35)' }, { transform: 'scale(1)' }],
            { duration: 420, easing: 'ease-out' })
        }
        return true
      })
      setShowTop(true)
    }
  }, [consent])

  // 每次開啟都重來：未讀完、未勾選（在 render 中依 open 的變化重設，不用 effect 連鎖 setState）
  const [wasOpen, setWasOpen] = useState(open)
  if (open !== wasOpen) {
    setWasOpen(open)
    if (open) {
      setReached(!consent)
      setChecked(!consent)
      setShowTop(false)
      setBusy(false)
      setError(null)
    }
  }
  useEffect(() => {
    if (!open) return
    doc.current?.scrollTo(0, 0)
    measure()
  }, [open, measure])

  const host = document.querySelector('.rd') ?? document.body
  const edition = terms.meta.split('・')[0]
  const canAccept = consent && reached && checked && !outdated && !busy

  async function accept() {
    if (!canAccept || !onAccept) return
    setBusy(true)
    setError(null)
    try {
      await onAccept()
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '送出失敗，請稍後再試')
      setBusy(false)
    }
  }

  let hint = ''
  let hintOk = false
  if (outdated) {
    hint = '網站內容已更新，請重新整理頁面後再閱讀並同意。'
  } else if (consent) {
    hint = reached ? '已經讀完了，請勾選同意。' : '請讀到最下面，才能勾選同意。'
    hintOk = reached
  } else if (acceptedAt) {
    hint = `你已於 ${formatDateTime(acceptedAt)} 同意這份須知（${edition}）。`
    hintOk = true
  }

  return createPortal(
    <Dialog open={open} onClose={onClose} className="terms" labelledBy="terms-title" dismissible={!busy}>
      <div className="terms__head">
        <div className="terms__brand">線上聖誕老人 2026</div>
        <h2 className="terms__title" id="terms-title">{terms.title}</h2>
        <div className="terms__meta">{terms.meta}</div>
        <button type="button" className="terms__close" aria-label={consent ? '取消，回到上一步' : '關閉'}
          onClick={onClose} disabled={busy}>
          <X className="icon" size={20} aria-hidden />
        </button>
      </div>
      <div className="terms__progress" aria-hidden="true"><i style={{ width: `${progress}%` }} /></div>
      <div ref={doc} className="terms__doc" tabIndex={0} aria-label="條款全文" onScroll={measure}>
        {terms.body}
      </div>
      <div className="terms__foot">
        {hint && <p className={`terms__hint${hintOk ? ' is-ok' : ''}`} aria-live="polite">{hint}</p>}
        {(consent || acceptedAt) && (
          <label className={`terms__chk${consent && !reached ? ' is-off' : ''}`}>
            <input ref={checkbox} type="checkbox" checked={checked}
              disabled={!consent || !reached || outdated}
              onChange={(event) => setChecked(event.target.checked)} />
            <span>{terms.consentLabel}</span>
          </label>
        )}
        {error && <p className="terms__hint" role="alert" style={{ color: '#fca5a5' }}>{error}</p>}
        <div className="terms__actions">
          {showTop && (
            <button type="button" className="link-btn terms__top"
              onClick={() => doc.current?.scrollTo({ top: 0, behavior: reducedMotion() ? 'auto' : 'smooth' })}>
              ↑ 回到頂端
            </button>
          )}
          <button type="button" className="btn" onClick={onClose} disabled={busy}>
            {consent ? '取消' : '關閉'}
          </button>
          {consent && (
            <button type="button" className="btn btn--primary" disabled={!canAccept} onClick={() => void accept()}>
              {busy ? '處理中…' : acceptLabel}
            </button>
          )}
        </div>
      </div>
    </Dialog>,
    host,
  )
}
