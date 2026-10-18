import { X } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { reducedMotion } from '../../lib/motion'
import { Dialog } from '../ui/Dialog'
import type { TermsDocument } from './termsContent'

/**
 * 參與須知全文對話框（依 prototype.html 的 termsDialog）：頭部、讀取進度條、可捲動的全文、
 * 底部「回到頂端」與「關閉」。目前只有唯讀檢視；註冊時「讀到最下面才能勾選同意」是之後的功能
 * （見 docs/redesign-1018-deferred.md #4），到時候在這裡加 `onAccept` 與勾選區即可。
 *
 * 可以開在另一個對話框之上（例如認領確認視窗）：Esc 與點背景只會關掉最上面這一個。
 * 放進 `.rd` 的 portal 而不是原地渲染——原地的話 `<dialog>` 會落在 `<p>` 裡，不合法的巢狀。
 */
export function TermsDialog({ open, onClose, terms }: {
  open: boolean
  onClose: () => void
  terms: TermsDocument
}) {
  const doc = useRef<HTMLDivElement>(null)
  const [progress, setProgress] = useState(0)
  const [showTop, setShowTop] = useState(false)

  const measure = useCallback(() => {
    const element = doc.current
    if (!element) return
    const max = element.scrollHeight - element.clientHeight
    setProgress(max > 0 ? Math.min(100, Math.round(element.scrollTop / max * 100)) : 100)
    setShowTop(element.scrollTop >= 200)
  }, [])

  useEffect(() => {
    if (!open) return
    doc.current?.scrollTo(0, 0)
    measure()
  }, [open, measure])

  const host = document.querySelector('.rd') ?? document.body

  return createPortal(
    <Dialog open={open} onClose={onClose} className="terms" labelledBy="terms-title">
      <div className="terms__head">
        <div className="terms__brand">線上聖誕老人 2026</div>
        <h2 className="terms__title" id="terms-title">{terms.title}</h2>
        <div className="terms__meta">{terms.meta}</div>
        <button type="button" className="terms__close" aria-label="關閉" onClick={onClose}>
          <X className="icon" size={20} aria-hidden />
        </button>
      </div>
      <div className="terms__progress" aria-hidden="true"><i style={{ width: `${progress}%` }} /></div>
      <div ref={doc} className="terms__doc" tabIndex={0} aria-label="條款全文" onScroll={measure}>
        {terms.body}
      </div>
      <div className="terms__foot">
        <div className="terms__actions">
          {showTop && (
            <button type="button" className="link-btn terms__top"
              onClick={() => doc.current?.scrollTo({ top: 0, behavior: reducedMotion() ? 'auto' : 'smooth' })}>
              ↑ 回到頂端
            </button>
          )}
          <button type="button" className="btn" onClick={onClose}>關閉</button>
        </div>
      </div>
    </Dialog>,
    host,
  )
}
