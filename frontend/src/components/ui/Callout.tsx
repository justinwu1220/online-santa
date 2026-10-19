import type { ReactNode } from 'react'

/** 提醒框（設計稿 `.callout`）：warn＝黃色提醒，ok＝綠色已完成。 */
export function Callout({ tone = 'warn', icon, className, children }: {
  tone?: 'warn' | 'ok'
  icon: ReactNode
  className?: string
  children: ReactNode
}) {
  return (
    <div className={`callout callout--${tone}${className ? ` ${className}` : ''}`}>
      {icon}
      <p>{children}</p>
    </div>
  )
}
