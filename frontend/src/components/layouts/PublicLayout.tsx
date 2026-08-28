import { useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useSearchParams } from 'react-router-dom'
import { useAuth, usingFirebase } from '../../lib/authContext'
import { useCurrentUser } from '../../lib/useCurrentUser'
import { Button, TextInput } from '../Form'

/**
 * 主網站。
 *
 * 願望牆對所有人公開——人要先看見孩子的願望才會考慮註冊，把牆擋在登入後會直接
 * 殺掉轉換率。認領與查看紀錄才需要登入。
 *
 * 機構入口放在頁尾，不放主導覽：捐贈者是主要對象，機構是少數但知道自己要找什麼。
 * 監控中心完全不出現在這裡。
 */
export function PublicLayout() {
  const { email } = useAuth()

  return (
    <div className="flex min-h-screen flex-col bg-santa-50">
      <header className="border-b border-santa-100 bg-white">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-4 py-3">
          <Link to="/" className="text-lg font-bold text-santa-700">
            🎄 線上聖誕老公公
          </Link>

          <nav className="flex flex-wrap gap-1">
            <NavLink to="/" end className={navLinkClass}>願望牆</NavLink>
            {email && <NavLink to="/me/claims" className={navLinkClass}>我的認領</NavLink>}
          </nav>

          <AuthControls />
        </div>
      </header>

      {!usingFirebase && <DevModeBanner />}

      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
        <Outlet />
      </main>

      <footer className="border-t border-santa-100 bg-white">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-4 py-6
          text-sm text-slate-500">
          <p>願望屬於孩子，故事屬於每一個願意伸手的人。</p>
          <Link to="/org/login" className="text-santa-700 hover:underline">
            我是兒童機構 →
          </Link>
        </div>
      </footer>
    </div>
  )
}

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  `rounded-md px-3 py-2 text-sm font-medium transition-colors ${
    isActive ? 'bg-santa-100 text-santa-700' : 'text-slate-600 hover:text-santa-600'
  }`

function AuthControls() {
  const { email, loading, signIn, signOut } = useAuth()
  const me = useCurrentUser()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const [draft, setDraft] = useState('')

  if (loading) {
    return <span className="text-sm text-slate-400">載入中…</span>
  }

  if (email) {
    return (
      <div className="flex items-center gap-3">
        {/* 機構成員誤入主網站時，給他一條回後台的路 */}
        {me.data?.role === 'ORG_MEMBER' && (
          <Link to="/org" className="text-sm text-santa-700 hover:underline">機構後台</Link>
        )}
        <span className="max-w-[14rem] truncate text-sm text-slate-500" title={email}>
          {email}
        </span>
        <Button variant="ghost" onClick={() => void signOut()}>登出</Button>
      </div>
    )
  }

  // 登入後回到原本的位置——分享出去的願望連結點進來時特別重要
  const next = searchParams.get('next') ?? location.pathname + location.search

  if (usingFirebase) {
    return <Button onClick={() => void signIn()}>使用 Google 登入</Button>
  }

  return (
    <form
      className="flex items-center gap-2"
      onSubmit={(event) => {
        event.preventDefault()
        void signIn(draft).then(() => {
          if (next && next !== '/') window.history.replaceState(null, '', next)
        })
      }}
    >
      <TextInput
        type="email"
        required
        value={draft}
        placeholder="you@example.com"
        onChange={(event) => setDraft(event.target.value)}
        className="w-52"
      />
      <Button type="submit">登入</Button>
    </form>
  )
}

function DevModeBanner() {
  return (
    <div className="bg-amber-50 px-4 py-2 text-center text-xs text-amber-900">
      開發模式：未設定 Firebase，身分僅由 <code>X-Dev-User-Email</code> 標頭指定，沒有任何驗證。
    </div>
  )
}
