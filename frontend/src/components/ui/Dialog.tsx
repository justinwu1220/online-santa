import { useEffect, useRef, type ReactNode } from 'react'

/**
 * 原生 `<dialog>` 的對話框（Esc 可關閉、焦點自動處理）。
 *
 * 遮罩的淡入淡出只靠 CSS（`::backdrop` 每次開啟都重新產生），不用 JS 對遮罩做動畫——
 * 那會在關閉後留下「透明度 0」的動畫，下次打開遮罩就不見（SPEC 15.4）。
 *
 * @param dismissible false 時不能用 Esc 或點背景關閉（例如送出中）
 */
export function Dialog({ open, onClose, children, className = 'dialog', dismissible = true, labelledBy }: {
  open: boolean
  onClose: () => void
  children: ReactNode
  className?: string
  dismissible?: boolean
  labelledBy?: string
}) {
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const element = ref.current
    if (!element) return
    if (open && !element.open) element.showModal()
    if (!open && element.open) element.close()
  }, [open])

  return (
    <dialog
      ref={ref}
      className={className}
      aria-labelledby={labelledBy}
      onCancel={(event) => {
        // 原生的 Esc 預設會直接關掉；先擋下，由外層決定要不要關
        event.preventDefault()
        if (dismissible) onClose()
      }}
      onClick={(event) => {
        // 點到 dialog 本身（背景區域）而不是裡面的內容
        if (dismissible && event.target === ref.current) onClose()
      }}
    >
      {children}
    </dialog>
  )
}
