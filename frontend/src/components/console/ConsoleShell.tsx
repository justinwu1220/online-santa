import { useEffect, useRef, type ReactNode } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../lib/authContext'
import { reducedMotion, wait } from '../../lib/motion'
import { Snowfall } from '../Snowfall'
import { ToastProvider } from '../ui/Toast'
import { OnboardHost } from '../onboard/OnboardHost'
import { VerifyBar } from '../ui/VerifyBar'

export interface ConsoleNavItem {
  to: string
  label: string
  /** 圖示的 SVG 內容（設計稿的 path，直接放進 24x24 的 viewBox） */
  icon: ReactNode
  /** 紅色未讀數字；0 或沒給就不顯示 */
  badge?: number
  end?: boolean
}

/**
 * 後台的應用程式版型（機構後台與管理員後台共用，設計稿 15.2）：
 * 隱藏網站頁首，左側整排選單（寬 260px、滿版高度），右側內容最大寬 1160px；
 * ≤860px 時選單改成頁面上方一排。
 *
 * 選單最上方是「線上聖誕老人」連結（點了回到願望牆）；接著後台名稱與機構名稱、選單項目；
 * 最下方是須知（`terms`）與帳號信箱、登出。
 *
 * 切換選單：右側內容淡出 0.12s → 由下 10px 浮上 0.3s；選單本身不動。
 */
export function ConsoleShell({ appLabel, name, items, terms, banner, children }: {
  appLabel: string
  name: string
  items: ConsoleNavItem[]
  /** 選單下方的「合作機構參與須知」之類的按鈕 */
  terms?: ReactNode
  /** 內容區最上方的提示（機構資料審核中…） */
  banner?: ReactNode
  children?: ReactNode
}) {
  const { email, signOut } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const main = useRef<HTMLDivElement>(null)
  const first = useRef(true)

  // 換頁後：右側內容由下 10px 浮上（第一次進來不播）
  useEffect(() => {
    if (first.current) {
      first.current = false
      return
    }
    window.scrollTo(0, 0)
    if (reducedMotion()) return
    main.current?.animate(
      [{ opacity: 0, transform: 'translateY(10px)' }, { opacity: 1, transform: 'none' }],
      { duration: 300, easing: 'cubic-bezier(.22,.8,.3,1)', fill: 'backwards' })
  }, [location.pathname])

  /** 點選單：右側內容先淡出 0.12s，再換頁。 */
  async function go(event: React.MouseEvent, to: string) {
    if (event.metaKey || event.ctrlKey || event.shiftKey || event.button !== 0) return
    event.preventDefault()
    if (location.pathname === to) return
    if (!reducedMotion() && main.current) {
      main.current.animate(
        [{ opacity: 1 }, { opacity: 0, transform: 'translateY(6px)' }],
        { duration: 120, easing: 'ease-in', fill: 'forwards' })
      await wait(120)
    }
    navigate(to)
    // 新內容的浮上動畫由上面的 effect 接手；先把淡出的殘留效果清掉
    main.current?.getAnimations().forEach((animation) => {
      if (animation.effect && (animation.effect as KeyframeEffect).getKeyframes().length === 2
        && animation.playState === 'finished') animation.cancel()
    })
  }

  return (
    <div className="rd is-app">
      <Snowfall />
      <ToastProvider>
        <OnboardHost>
          <VerifyBar />
          <div className="console-page">
            <div className="org-app">
              <aside className="org-side" aria-label={`${appLabel}選單`}>
                <Link className="brand side-brand" to="/">
                  <svg className="icon" width="20" height="20" viewBox="0 0 24 24" stroke="#6ee7b7" aria-hidden>
                    <path d="m12 2-6 9h3l-4 6h4l-2 5h10l-2-5h4l-4-6h3z" />
                  </svg>
                  <span className="brand__text">線上聖誕老人</span>
                </Link>
                <div className="org-side__who">
                  <span className="org-side__app">{appLabel}</span>
                  <strong>{name}</strong>
                </div>
                <nav className="org-nav" aria-label={appLabel}>
                  {items.map((item) => (
                    <NavLink key={item.to} to={item.to} end={item.end}
                      className="org-nav__item"
                      onClick={(event) => void go(event, item.to)}>
                      <svg className="icon" viewBox="0 0 24 24" aria-hidden>{item.icon}</svg>
                      <span>{item.label}</span>
                      {item.badge ? (
                        <span className="org-nav__badge" aria-label={`${item.badge} 則新訊息`}>{item.badge}</span>
                      ) : null}
                    </NavLink>
                  ))}
                </nav>
                {terms}
                <div className="side-account">
                  <span>{email}</span>
                  <button type="button" className="account__out" onClick={() => void signOut()}>登出</button>
                </div>
              </aside>

              <div className="org-main" ref={main}>
                {banner}
                {children ?? <Outlet />}
              </div>
            </div>
          </div>
        </OnboardHost>
      </ToastProvider>
    </div>
  )
}
