import type { ReactNode } from 'react'
import { EASE_OUT, reducedMotion } from '../../lib/motion'

/** 抽屜裡用的圖示（設計稿 DICON 的 path，24x24）。 */
const ICONS = {
  truck: (
    <>
      <path d="M14 18V6a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v11a1 1 0 0 0 1 1h2" /><path d="M15 18H9" />
      <path d="M19 18h2a1 1 0 0 0 1-1v-3.65a1 1 0 0 0-.22-.62l-3.48-4.35A1 1 0 0 0 17.52 8H14" />
      <circle cx="17" cy="18" r="2" /><circle cx="7" cy="18" r="2" />
    </>
  ),
  user: (<><path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" /></>),
  gift: (
    <>
      <rect x="3" y="8" width="18" height="4" rx="1" /><path d="M12 8v13" />
      <path d="M19 12v7a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-7" />
      <path d="M7.5 8a2.5 2.5 0 0 1 0-5A4.8 8 0 0 1 12 8a4.8 8 0 0 1 4.5-5 2.5 2.5 0 0 1 0 5" />
    </>
  ),
  building: (
    <>
      <path d="M6 22V4a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v18Z" /><path d="M6 12H4a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h2" />
      <path d="M18 9h2a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-2" /><path d="M10 6h4" /><path d="M10 10h4" />
      <path d="M10 14h4" /><path d="M10 18h4" />
    </>
  ),
  pin: (
    <>
      <path d="M20 10c0 4.99-5.54 10.19-7.4 11.8a1 1 0 0 1-1.2 0C9.54 20.19 4 14.99 4 10a8 8 0 0 1 16 0" />
      <circle cx="12" cy="10" r="3" />
    </>
  ),
  clock: (<><circle cx="12" cy="12" r="10" /><path d="M12 6v6l4 2" /></>),
  check: <path d="M20 6 9 17l-5-5" />,
  alert: (
    <>
      <path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3" />
      <path d="M12 9v4" /><path d="M12 17h.01" />
    </>
  ),
  pencil: <path d="M21.17 6.81a1 1 0 0 0-3.99-3.99L3.84 16.17a2 2 0 0 0-.5.83l-1.32 4.35a.5.5 0 0 0 .62.62l4.35-1.32a2 2 0 0 0 .83-.5z" />,
  search: (<><circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" /></>),
  spark: (<><path d="M12 3l1.9 5.8L20 10l-6.1 1.2L12 17l-1.9-5.8L4 10l6.1-1.2z" /><path d="M19 17v4" /><path d="M17 19h4" /></>),
  snow: (
    <>
      <path d="M2 12h20" /><path d="M12 2v20" /><path d="m20 16-4-4 4-4" /><path d="m4 8 4 4-4 4" />
      <path d="m16 4-4 4-4-4" /><path d="m8 20 4-4 4 4" />
    </>
  ),
} as const

export type DrawerIcon = keyof typeof ICONS

export function DIcon({ name }: { name: DrawerIcon }) {
  return <svg className="icon" viewBox="0 0 24 24" aria-hidden>{ICONS[name]}</svg>
}

/** 狀態卡：顏色＝狀態，標題寫「現在怎樣」，說明寫「下一步要做什麼」。 */
export function DStatus({ tone, icon, title, desc }: {
  tone: string
  icon: DrawerIcon
  title: ReactNode
  desc?: ReactNode
}) {
  return (
    <div className="dstatus" data-tone={tone}>
      <span className="dstatus__icon"><DIcon name={icon} /></span>
      <div>
        <strong className="dstatus__title">{title}</strong>
        {desc && <p className="dstatus__desc">{desc}</p>}
      </div>
    </div>
  )
}

export type StepState = 'done' | 'now' | 'todo' | 'late'

/** 進度：認領 → 寄出 → 收到。完成＝勾、目前這步＝金色圈、逾期＝紅色驚嘆號；已完成整條轉灰。 */
export function DSteps({ steps, complete }: {
  steps: { label: string; sub: string; state: StepState }[]
  complete?: boolean
}) {
  return (
    <ol className={`dsteps${complete ? ' is-complete' : ''}`} aria-label="進度">
      {steps.map((step) => (
        <li key={step.label} className={`dstep is-${step.state}`}>
          <span className="dstep__dot">
            {step.state === 'done' ? <DIcon name="check" /> : step.state === 'late' ? '!' : null}
          </span>
          <strong>{step.label}</strong>
          <span>{step.sub}</span>
        </li>
      ))}
    </ol>
  )
}

/** 資訊卡。 */
export function DCard({ icon, title, className = '', children }: {
  icon: DrawerIcon
  title: string
  className?: string
  children: ReactNode
}) {
  return (
    <section className={`dcard ${className}`}>
      <h3 className="dcard__title"><DIcon name={icon} />{title}</h3>
      {children}
    </section>
  )
}

/** 名稱／值的兩欄清單。 */
export function Kv({ rows }: { rows: { label: string; value: ReactNode; full?: boolean }[] }) {
  return (
    <dl className="kv">
      {rows.map((row) => (
        <div key={row.label} className={row.full ? 'kv__full' : undefined}>
          <dt>{row.label}</dt>
          <dd>{row.value}</dd>
        </div>
      ))}
    </dl>
  )
}

/** 抽屜的分頁列（有認領者或有系統訊息時才出現）。 */
export function DrawerTabs({ tabs, active, onChange }: {
  tabs: { key: string; label: string; unread?: number }[]
  active: string
  onChange: (key: string) => void
}) {
  if (tabs.length === 0) return null
  return (
    <div className="drawer__tabs" role="tablist" aria-label="切換內容">
      {tabs.map((tab) => (
        <button key={tab.key} type="button" role="tab" className="dtab"
          aria-selected={tab.key === active}
          onClick={() => {
            if (tab.key === active) return
            onChange(tab.key)
            // 換分頁：新內容由下 6px 浮上 0.2s（設計稿 setDrawerTab）
            if (!reducedMotion()) {
              window.requestAnimationFrame(() => document.querySelector('dialog.drawer[open] .drawer__body')?.animate(
                [{ opacity: 0, transform: 'translateY(6px)' }, { opacity: 1, transform: 'none' }],
                { duration: 200, easing: EASE_OUT }))
            }
          }}>
          {tab.label}
          {tab.unread ? <span className="dtab__n" aria-label={`${tab.unread} 則新訊息`}>{tab.unread}</span> : null}
        </button>
      ))}
    </div>
  )
}
