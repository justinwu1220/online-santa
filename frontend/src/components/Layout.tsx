import { Link, NavLink, Outlet } from 'react-router-dom'

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  `px-3 py-2 rounded-md text-sm font-medium transition-colors ${
    isActive ? 'bg-santa-100 text-santa-700' : 'text-slate-600 hover:text-santa-600'
  }`

export function Layout() {
  return (
    <div className="min-h-screen flex flex-col">
      <header className="border-b border-santa-100 bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
          <Link to="/" className="text-lg font-bold text-santa-700">
            🎄 線上聖誕老公公
          </Link>
          <nav className="flex gap-1">
            <NavLink to="/" className={navLinkClass} end>
              願望牆
            </NavLink>
            <NavLink to="/me/claims" className={navLinkClass}>
              我的認領
            </NavLink>
            <NavLink to="/org" className={navLinkClass}>
              機構後台
            </NavLink>
          </nav>
        </div>
      </header>

      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
        <Outlet />
      </main>

      <footer className="border-t border-santa-100 py-6 text-center text-sm text-slate-500">
        願望屬於孩子，故事屬於每一個願意伸手的人。
      </footer>
    </div>
  )
}
