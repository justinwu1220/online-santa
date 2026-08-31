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
    <div className="flex min-h-screen flex-col bg-slate-50">
      <header className={headerClass}>
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
        <nav className="md:w-52 md:shrink-0">
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
