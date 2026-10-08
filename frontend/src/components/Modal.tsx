import { useEffect, type ReactNode } from 'react'

/**
 * 全站第一個頁面級彈窗。刻意不用 portal——專案目前完全沒用到 portal，
 * 用 `fixed inset-0` 定位就夠了，而且留在原本的 React tree 裡剛好讓它
 * 自然吃到祖先層的 `.theme-night` class（深色主題靠這個 class 切換）。
 */
export function Modal({ title, onClose, children }: {
  title: string; onClose: () => void; children: ReactNode
}) {
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => { if (event.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
      onClick={onClose}>
      <div className="glass-card w-full max-w-md p-6" onClick={(event) => event.stopPropagation()}>
        <div className="mb-4 flex items-center justify-between gap-3">
          <h2 className="text-lg font-semibold text-white">{title}</h2>
          <button type="button" onClick={onClose} aria-label="關閉"
            className="text-slate-400 hover:text-white">✕</button>
        </div>
        {children}
      </div>
    </div>
  )
}
