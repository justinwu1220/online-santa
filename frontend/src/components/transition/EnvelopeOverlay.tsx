import { forwardRef } from 'react'

/**
 * 拆信轉場的遮罩與信封（SPEC 6.1）。位置、顯示與動畫由 TransitionProvider 以 class 控制：
 * `is-on` 擋住點擊、`is-visible` 淡入、`is-playing` 播信封。頂端對齊頁首底部，頁首不被蓋住。
 */
export const EnvelopeOverlay = forwardRef<HTMLDivElement>(function EnvelopeOverlay(_props, ref) {
  return (
    <div ref={ref} className="env-overlay" aria-hidden="true">
      <div className="env">
        <svg width="160" height="160" viewBox="0 -30 120 120">
          <rect x="5" y="20" width="110" height="65" rx="8" fill="#16234a" stroke="#fde68a" strokeWidth="1.6" />
          <g className="env-flap">
            <path d="M5 28 L60 62 L115 28 Q115 20 107 20 H13 Q5 20 5 28 Z" fill="#22356a" stroke="#fde68a"
              strokeWidth="1.6" strokeLinejoin="round" />
            <circle className="env-seal" cx="60" cy="56" r="7" fill="#f87171" />
            <path d="M60 51.5v9M55.5 56h9" stroke="#fff7ed" strokeWidth="1.6" strokeLinecap="round" />
          </g>
          <g className="env-letter">
            <rect x="18" y="26" width="84" height="54" rx="4" fill="#fbf8f1" />
            <path d="M28 38h50M28 47h64M28 56h40" stroke="#c9b99a" strokeWidth="2.4" strokeLinecap="round" />
          </g>
          <path d="M5 28 L60 60 L115 28 V77 Q115 85 107 85 H13 Q5 85 5 77 Z" fill="#1b2b58" stroke="#fde68a"
            strokeWidth="1.6" strokeLinejoin="round" />
        </svg>
        <span className="env__label">打開信件</span>
      </div>
    </div>
  )
})
