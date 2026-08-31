import { useEffect, type ReactNode } from 'react'
import { Link, NavLink } from 'react-router-dom'
import { useAuth } from '../../lib/authContext'
import { BRAND, pageTitle } from '../../lib/brand'
import { EmailVerificationBanner } from '../EmailVerificationBanner'
import { Button } from '../Form'

export interface ConsoleNavItem {
  to: string
  label: string
  end?: boolean
  /** 顯示在項目右側的數字，例如待審核筆數。0 或 undefined 時不顯示 */
  badge?: number
}

/**
 * 頂部工具列的高度，與下方側導覽 sticky 的 top 偏移共用同一個數字——量測自實際
 * 渲染結果（py-3 的內距 + text-base 單行內容的行高）。兩處各自寫死同一個值很容易
 * 之後改了 header 卻忘記改 nav，所以抽成一個常數，只有一個地方要對。
 *
 * 只在 md 以上生效：手機版 header 的內容會 flex-wrap 成兩行，高度不固定，
 * sticky offset 一旦跟著跑掉就會讓側欄卡進 header 底下；小螢幕本來就該讓 nav
 * 留在內容前面而非常駐佔位，所以手機版兩者都不做 sticky（見下方 nav 的說明）。
 */
const HEADER_HEIGHT_PX = 60
const NAV_TOP_GAP_PX = 24
const NAV_STICKY_TOP_PX = HEADER_HEIGHT_PX + NAV_TOP_GAP_PX

/**
 * 後台的共用外框：側邊導覽 + 頂部工具列。
 *
 * 與主網站刻意做出區隔——後台的使用者是在「工作」，需要的是資訊密度與穩定的導覽
 * 位置，不是溫暖的視覺。機構後台與監控中心共用這個骨架，只有色調與導覽項目不同。
 */
export function ConsoleLayout({ title, subtitle, accent, items, homePath, children }: {
  title: string
  subtitle?: string
  /** 頂條的色調。機構用綠、監控中心用深灰，讓人一眼知道自己在哪個系統 */
  accent: 'santa' | 'slate'
  items: ConsoleNavItem[]
  homePath: string
  children: ReactNode
}) {
  const { email, signOut } = useAuth()

  // 後台的人常常同時開著好幾個分頁（機構後台、監控中心、主網站），
  // 分頁標題要分得出來
  useEffect(() => {
    document.title = pageTitle(title)
    return () => { document.title = pageTitle() }
  }, [title])

  const headerClass = accent === 'santa'
    ? 'bg-santa-700 text-white'
    : 'bg-slate-800 text-white'

  return (
    <div
      className="flex min-h-screen flex-col bg-slate-50"
      style={{ '--console-nav-top': `${NAV_STICKY_TOP_PX}px` } as React.CSSProperties}
    >
      {/* z-40：要壓得住頁面內容裡的 Recharts 圖表與 glass/ring 卡片，但留出空間
          給日後可能出現的 modal／toast（那些理當更高） */}
      <header className={`${headerClass} md:sticky md:top-0 md:z-40`}>
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-3 px-4 py-3">
          {/*
            logo 指向自己的首頁，不會把人丟回主網站。

            平台名稱擺在後台名稱之前：後台的人多半是被信件或連結直接帶進來的，
            畫面上如果只有「機構後台」，他不見得知道自己在哪一個平台。名稱用較小的
            字級與較低的不透明度，主角仍然是「你在哪一個後台」。
          */}
          <Link to={homePath} className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
            <span className="text-sm opacity-80">{BRAND}</span>
            <span aria-hidden className="text-sm opacity-40">/</span>
            <span className="text-base font-semibold">{title}</span>
            {subtitle && <span className="text-sm opacity-75">{subtitle}</span>}
          </Link>

          <div className="flex items-center gap-3">
            <span className="max-w-[16rem] truncate text-sm opacity-90" title={email ?? ''}>
              {email}
            </span>
            <Button
              variant="ghost"
              className="text-white hover:bg-white/10"
              onClick={() => void signOut()}
            >
              登出
            </Button>
          </div>
        </div>
      </header>

      <EmailVerificationBanner />

      <div className="mx-auto flex w-full max-w-7xl flex-1 flex-col gap-6 px-4 py-6 md:flex-row">
        {/*
          手機版維持現狀：nav 水平 wrap 在內容上方、隨頁面捲動——螢幕太窄，
          常駐側欄會吃掉本來就有限的內容空間，不值得。所有 sticky／max-height
          都掛在 md: 前綴下，手機版完全不受影響。

          top／max-height 讀 --console-nav-top 這個 CSS 變數（在外層容器上設定，
          值等於 NAV_STICKY_TOP_PX）：用 Tailwind 的 arbitrary value 語法
          `[var(--x)]` 而不是把 JS 常數直接內插進 class 字串——後者是動態組出來的
          字串，Tailwind 的建置期掃描器看不到，永遠不會產生對應的 CSS。
        */}
        <nav
          className="md:sticky md:top-[var(--console-nav-top)] md:w-52 md:shrink-0
            md:max-h-[calc(100vh-var(--console-nav-top))] md:self-start md:overflow-y-auto"
        >
          <ul className="flex flex-wrap gap-1 md:flex-col">
            {items.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) =>
                    `flex items-center justify-between gap-2 rounded-lg px-3 py-2 text-sm
                     font-medium transition-colors ${
                      isActive
                        ? 'bg-white text-slate-900 shadow-sm ring-1 ring-slate-200'
                        : 'text-slate-600 hover:bg-white/60'
                    }`
                  }
                >
                  <span>{item.label}</span>
                  {item.badge ? (
                    <span className="rounded-full bg-berry-500 px-1.5 py-0.5 text-xs text-white">
                      {item.badge}
                    </span>
                  ) : null}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>

        <main className="min-w-0 flex-1">{children}</main>
      </div>
    </div>
  )
}

/** 後台頁面的標準區塊。比前台的卡片更方正、留白更少。 */
export function ConsolePanel({ title, action, children }: {
  title?: string
  action?: ReactNode
  children: ReactNode
}) {
  return (
    <section className="rounded-lg bg-white p-5 ring-1 ring-slate-200">
      {(title || action) && (
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          {title && <h2 className="font-semibold text-slate-800">{title}</h2>}
          {action}
        </div>
      )}
      {children}
    </section>
  )
}
