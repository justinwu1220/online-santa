import { Fragment, useEffect, useRef, useState, type ReactNode } from 'react'
import { burst, center, EASE_OUT, reducedMotion, wait } from '../../lib/motion'
import { Dialog } from '../ui/Dialog'
import type { OnboardCard } from './onboardContent'

/**
 * 首次登入說明（「開始之前」彈窗，依 prototype.html 的 onboardDialog）。
 *
 * 一張一張的卡片：插圖、標題、說明、圓點進度、上一步／下一步。最後一張按主要按鈕才算看完
 * （撒星星 → 等 250ms → 關閉 → `onFinish`）。第一次看的時候不能用 Esc 或點背景略過
 * （`dismissible=false`）；使用者之後自己從右上角的 ⓘ 重看時可以直接關掉。
 *
 * 內容（`cards`、`illustrations`）是我們自己的靜態資料，所以插圖與說明裡的 <b> 直接以 HTML 渲染。
 */
export function OnboardDialog({
  open, cards, illustrations, dismissible, links, onClose, onFinish,
}: {
  open: boolean
  cards: OnboardCard[]
  illustrations: Record<string, string>
  /** 可以用 Esc／點背景關閉（重看時為 true） */
  dismissible: boolean
  /**
   * 說明文字裡的某段文字換成可互動的元件（例如把《認領者參與須知》換成會開啟須知的連結）。
   * key 是要被取代的原文，出現幾次就換幾次。
   */
  links?: Record<string, ReactNode>
  onClose: () => void
  /** 看完最後一張（關閉動畫之前呼叫；呼叫端負責關掉、記錄、顯示提示） */
  onFinish: () => void
}) {
  const [index, setIndex] = useState(0)
  // 換張的方向（1 往後、-1 往前、0 剛開啟），決定插圖與文字從哪一側滑進來
  const [direction, setDirection] = useState<1 | -1 | 0>(0)
  const [finishing, setFinishing] = useState(false)
  const illo = useRef<HTMLDivElement>(null)
  const text = useRef<HTMLDivElement>(null)
  const next = useRef<HTMLButtonElement>(null)
  const prev = useRef<HTMLButtonElement>(null)

  const [wasOpen, setWasOpen] = useState(open)
  if (open !== wasOpen) {
    setWasOpen(open)
    if (open) {
      setIndex(0)
      setDirection(0)
      setFinishing(false)
    }
  }

  const card = cards[index]
  const last = index === cards.length - 1

  // 開啟：插圖由下 16px 浮上放大（0.52s），焦點給「下一步」
  useEffect(() => {
    if (!open) return
    if (!reducedMotion()) {
      illo.current?.animate(
        [{ opacity: 0, transform: 'translateY(16px) scale(.9)' }, { opacity: 1, transform: 'none' }],
        { duration: 520, delay: 120, easing: EASE_OUT, fill: 'backwards' })
    }
    next.current?.focus()
  }, [open])

  // 換張：插圖與文字依方向滑入（0.38s）
  useEffect(() => {
    if (!open || direction === 0 || reducedMotion()) return
    illo.current?.animate(
      [{ opacity: 0, transform: `translateX(${40 * direction}px) scale(.96)` }, { opacity: 1, transform: 'none' }],
      { duration: 380, easing: EASE_OUT, fill: 'backwards' })
    text.current?.animate(
      [{ opacity: 0, transform: `translateX(${24 * direction}px)` }, { opacity: 1, transform: 'none' }],
      { duration: 380, delay: 50, easing: EASE_OUT, fill: 'backwards' })
  }, [open, index, direction])

  async function goNext() {
    if (finishing) return
    if (!last) {
      setDirection(1)
      setIndex((i) => i + 1)
      return
    }
    setFinishing(true)
    if (next.current) burst(...center(next.current), 18, 110)
    await wait(reducedMotion() ? 0 : 250)
    onFinish()
  }

  function goPrev() {
    if (finishing || index === 0) return
    setDirection(-1)
    setIndex((i) => i - 1)
  }

  return (
    <Dialog open={open} onClose={onClose} className="onboard" labelledBy="ob-title" dismissible={dismissible}>
      <div
        onKeyDown={(event) => {
          if (event.key === 'ArrowRight') void goNext()
          if (event.key === 'ArrowLeft') goPrev()
        }}
        style={{ display: 'contents' }}
      >
        <div className="onboard__top">
          <span className="onboard__brand">
            <svg className="icon" width="18" height="18" viewBox="0 0 24 24" stroke="#6ee7b7" aria-hidden="true">
              <path d="m12 2-6 9h3l-4 6h4l-2 5h10l-2-5h4l-4-6h3z" />
            </svg>
            <span className="grad-text">線上聖誕老人</span>
          </span>
          <span className="onboard__step" aria-live="polite">{card.step}</span>
        </div>
        <div ref={illo} className="onboard__illo" aria-hidden="true"
          dangerouslySetInnerHTML={{ __html: illustrations[card.illo] ?? '' }} />
        <div ref={text} className="onboard__text">
          <h2 id="ob-title">{card.title}</h2>
          <p>{renderSub(card.sub, links)}</p>
        </div>
        <div className="onboard__foot">
          <div className="onboard__dots" aria-hidden="true">
            {cards.map((item, i) => <i key={item.step + i} className={i === index ? 'on' : ''} />)}
          </div>
          <div className="onboard__actions">
            <button ref={prev} type="button" className="btn" hidden={index === 0} onClick={goPrev}>上一步</button>
            <button ref={next} type="button" className="btn btn--primary" disabled={finishing}
              onClick={() => void goNext()}>
              {card.next}
            </button>
          </div>
        </div>
      </div>
    </Dialog>
  )
}

/**
 * 說明文字是我們自己的靜態 HTML（含 <b>）。把 `links` 指定的原文挖出來換成元件，
 * 其餘片段照 HTML 渲染。
 */
function renderSub(sub: string, links?: Record<string, ReactNode>): ReactNode {
  const tokens = Object.keys(links ?? {})
  if (!links || tokens.length === 0) return <span dangerouslySetInnerHTML={{ __html: sub }} />
  const pattern = new RegExp(`(${tokens.map((token) => token.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|')})`)
  return sub.split(pattern).map((part, i) => (
    <Fragment key={i}>
      {part in links ? links[part] : <span dangerouslySetInnerHTML={{ __html: part }} />}
    </Fragment>
  ))
}
