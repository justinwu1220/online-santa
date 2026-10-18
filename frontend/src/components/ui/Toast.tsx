import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { ToastContext, type ToastTone } from './toastContext'

/** 一般提示用綠色，錯誤與取消用紅色（SPEC 11.2）。3.2 秒後自動消失。 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<{ text: string; tone: ToastTone; id: number } | null>(null)
  const timer = useRef<number | undefined>(undefined)

  const show = useCallback((text: string, tone: ToastTone = 'ok') => {
    window.clearTimeout(timer.current)
    setToast({ text, tone, id: Date.now() })
    timer.current = window.setTimeout(() => setToast(null), 3200)
  }, [])

  useEffect(() => () => window.clearTimeout(timer.current), [])

  const value = useMemo(() => ({ show }), [show])

  return (
    <ToastContext.Provider value={value}>
      {children}
      {toast && (
        <div key={toast.id} role="status" aria-live="polite"
          className={`toast${toast.tone === 'danger' ? ' toast--danger' : ''}`}>
          {toast.text}
        </div>
      )}
    </ToastContext.Provider>
  )
}
