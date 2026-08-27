import { useState } from 'react'
import { Link, NavLink, Outlet } from 'react-router-dom'
import { useAuth, usingFirebase } from '../lib/authContext'
import { useCurrentUser } from '../lib/useCurrentUser'
import { Button, TextInput } from './Form'

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  `rounded-md px-3 py-2 text-sm font-medium transition-colors ${
    isActive ? 'bg-santa-100 text-santa-700' : 'text-slate-600 hover:text-santa-600'
  }`

export function Layout() {
  const { email } = useAuth()
  const me = useCurrentUser()

  const isOrgMember = me.data?.role === 'ORG_MEMBER'
  const isAdmin = me.data?.role === 'ADMIN'

  return (
    <div className="flex min-h-screen flex-col">
      <header className="border-b border-santa-100 bg-white">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-4 py-3">
          <Link to="/" className="text-lg font-bold text-santa-700">
            🎄 線上聖誕老公公
          </Link>

          <nav className="flex flex-wrap gap-1">
            <NavLink to="/" className={navLinkClass} end>願望牆</NavLink>
            {email && <NavLink to="/me/claims" className={navLinkClass}>我的認領</NavLink>}
            {email && !isAdmin && <NavLink to="/org" className={navLinkClass}>
              {isOrgMember ? '機構後台' : '機構註冊'}
            </NavLink>}
            {isAdmin && <NavLink to="/admin/organizations" className={navLinkClass}>審核後台</NavLink>}
          </nav>

          <AuthControls />
        </div>
      </header>

      {!usingFirebase && <DevModeBanner />}

      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
        <Outlet />
      </main>

      <footer className="border-t border-santa-100 py-6 text-center text-sm text-slate-500">
        願望屬於孩子，故事屬於每一個願意伸手的人。
      </footer>
    </div>
  )
}

function AuthControls() {
  const { email, loading, signIn, signOut } = useAuth()
  const [draft, setDraft] = useState('')

  if (loading) {
    return <span className="text-sm text-slate-400">載入中…</span>
  }

  if (email) {
    return (
      <div className="flex items-center gap-3">
        <span className="max-w-[16rem] truncate text-sm text-slate-500" title={email}>
          {email}
        </span>
        <Button variant="ghost" onClick={() => void signOut()}>登出</Button>
      </div>
    )
  }

  if (usingFirebase) {
    return <Button onClick={() => void signIn()}>使用 Google 登入</Button>
  }

  // 開發模式：直接輸入 email 切換身分，對應後端的 dev-auth
  return (
    <form
      className="flex items-center gap-2"
      onSubmit={(event) => {
        event.preventDefault()
        void signIn(draft)
      }}
    >
      <TextInput
        type="email"
        required
        value={draft}
        placeholder="you@example.com"
        onChange={(event) => setDraft(event.target.value)}
        className="w-56"
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
