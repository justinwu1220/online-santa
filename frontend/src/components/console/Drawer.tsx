import { X } from 'lucide-react'
import { useEffect, useRef, type ReactNode } from 'react'
import { reducedMotion } from '../../lib/motion'

const OPEN_MS = 320
const CLOSE_MS = 220
const EASE_OUT = 'cubic-bezier(.22,.8,.3,1)'

/**
 * 右側抽屜（設計稿 15.4）。原生 `<dialog>`，寬 600px（手機全寬）。
 *
 * - **只動位移**（translateX），不對整片抽屜做透明度動畫——文字在透明度動畫結束時會換抗鋸齒，
 *   看起來像閃一下。開啟 0.32s 減速、關閉 0.22s 加速。
 * - 遮罩的淡入淡出只用 CSS（關閉時先加 `.is-closing`，計時器到了才真的 close），
 *   不用 JS 對 `::backdrop` 做動畫（會在下次開啟時「先變暗又亮回來」）。
 * - 抽屜已經開著、只是內容更新（按完按鈕）時不會重播滑入。
 *
 * @param tone 抽屜的強調色（狀態色）：`data-tone` 驅動 CSS 變數，分頁底線、進度、引言線、主要按鈕都跟著變
 */
export function Drawer({ open, onClose, tone, labelledBy, title, chip, tabs, foot, children, dismissible = true }: {
  open: boolean
  onClose: () => void
  tone?: string
  labelledBy: string
  /** 抽屜標題 */
  title: ReactNode
  /** 標題左邊的狀態標籤 */
  chip?: ReactNode
  /** 分頁列（有的話放在標題與內容之間） */
  tabs?: ReactNode
  /** 底部動作按鈕；沒有就不畫 */
  foot?: ReactNode
  children: ReactNode
  /** 送出中不能用 Esc 或點背景關掉 */
  dismissible?: boolean
}) {
  const ref = useRef<HTMLDialogElement>(null)
  const titleRef = useRef<HTMLHeadingElement>(null)

  useEffect(() => {
    const element = ref.current
    if (!element) return
    if (open) {
      if (element.open && !element.classList.contains('is-closing')) return   // 已開著，只是換內容
      element.getAnimations({ subtree: true }).forEach((animation) => animation.cancel())
      element.classList.remove('is-closing')
      if (!element.open) element.showModal()
      titleRef.current?.focus()
      if (!reducedMotion()) {
        element.animate([{ transform: 'translateX(100%)' }, { transform: 'none' }],
          { duration: OPEN_MS, easing: EASE_OUT })
      }
      return
    }
    if (!element.open) return
    if (reducedMotion()) {
      element.close()
      return
    }
    element.classList.add('is-closing')
    element.animate([{ transform: 'none' }, { transform: 'translateX(100%)' }],
      { duration: CLOSE_MS, easing: 'cubic-bezier(.4,0,1,1)', fill: 'forwards' })
    // 用計時器，不等動畫事件（分頁在背景時那個事件可能永遠不會來）
    const timer = window.setTimeout(() => {
      element.close()
      element.classList.remove('is-closing')
      element.getAnimations({ subtree: true }).forEach((animation) => animation.cancel())
    }, CLOSE_MS)
    return () => window.clearTimeout(timer)
  }, [open])

  return (
    <dialog
      ref={ref}
      className="drawer"
      data-tone={tone}
      aria-labelledby={labelledBy}
      onCancel={(event) => {
        event.preventDefault()
        if (dismissible) onClose()
      }}
      onClick={(event) => {
        if (dismissible && event.target === ref.current) onClose()   // 點背景關閉
      }}
    >
      <div className="drawer__head">
        {chip}
        <h2 className="drawer__title" id={labelledBy} ref={titleRef} tabIndex={-1}>{title}</h2>
        <button type="button" className="drawer__close" aria-label="關閉" onClick={onClose}>
          <X className="icon" width={20} height={20} aria-hidden />
        </button>
      </div>
      {tabs}
      {children}
      {foot}
    </dialog>
  )
}
