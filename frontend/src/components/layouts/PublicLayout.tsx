import { useQuery } from '@tanstack/react-query'
import { TreePine, User } from 'lucide-react'
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import { api } from '../../lib/api'
import { useAuth, usingFirebase } from '../../lib/authContext'
import { PLATFORM_NAME } from '../../lib/brand'
import type { ClaimDonorView, PageResponse } from '../../lib/types'
import { effectiveRoleOf, useCurrentUser } from '../../lib/useCurrentUser'
import { Snowfall } from '../Snowfall'
import { TermsGate } from '../terms/TermsGate'
import { TransitionProvider } from '../transition/TransitionProvider'
import { ToastProvider } from '../ui/Toast'
import { VerifyBar } from '../ui/VerifyBar'

/**
 * 主網站。
 *
 * 願望牆對所有人公開——人要先看見孩子的願望才會考慮註冊，把牆擋在登入後會直接
 * 殺掉轉換率。認領與查看紀錄才需要登入。
 *
 * 沒有頁尾。機構從頁首的「登入 / 註冊」進入，註冊時選擇機構身分；登入後機構與管理員的
 * 後台按鈕出現在右上角。
 *
 * 頁首與雪花放在路由之外，換頁時不重新產生（SPEC 6.1 防閃爍的條件之一）。
 */
export function PublicLayout() {
  return (
    <div className="rd">
      <Snowfall />
      <ToastProvider>
        <TransitionProvider>
          <SiteHeader />
          {!usingFirebase && (
            <div className="bar bar--dev">
              開發模式：未設定 Firebase，身分僅由 <code>X-Dev-User-Email</code> 標頭指定，沒有任何驗證。
            </div>
          )}
          <VerifyBar />
          <TermsGate />
          <main className="page">
            <Outlet />
          </main>
        </TransitionProvider>
      </ToastProvider>
    </div>
  )
}

function SiteHeader() {
  const { email } = useAuth()

  return (
    <header className="site-header">
      <div className="site-header__in">
        <Link to="/" className="brand">
          <TreePine className="icon" size={22} aria-hidden style={{ color: '#4ade80' }} />
          <span className="brand__text">{PLATFORM_NAME}</span>
        </Link>

        <nav className="nav" aria-label="主選單">
          <NavLink to="/" end className="nav-link">願望牆</NavLink>
          {email && <MyClaimsLink />}
        </nav>

        <AuthControls />
      </div>
    </header>
  )
}

/** 「我的認領」與進行中的認領數（取消、收回的不算）。 */
function MyClaimsLink() {
  const me = useCurrentUser()
  const isDonor = effectiveRoleOf(me.data) === 'DONOR'

  const claims = useQuery({
    queryKey: ['claims', 'me', 'count'],
    queryFn: () => api.get<PageResponse<ClaimDonorView>>('/api/claims/me?size=100'),
    enabled: isDonor,
    staleTime: 30_000,
  })

  const active = claims.data?.content
    .filter((claim) => claim.status !== 'RELEASED' && claim.status !== 'CANCELLED').length ?? 0

  return (
    <NavLink to="/me/claims" className="nav-link">
      我的認領
      {active > 0 && <span className="nav-count" aria-label={`${active} 筆進行中`}>{active}</span>}
    </NavLink>
  )
}

function AuthControls() {
  const { email, loading, signOut } = useAuth()
  const me = useCurrentUser()
  const location = useLocation()

  if (loading) return <span style={{ color: 'var(--fg-muted)', fontSize: 14 }}>載入中…</span>

  if (!email) {
    // 帶上目前位置，登入後回得來——分享出去的願望連結點進來時特別重要
    const next = encodeURIComponent(location.pathname + location.search)
    return <Link to={`/login?next=${next}`} className="login">登入 / 註冊</Link>
  }

  // 用生效角色判斷——信箱還沒驗證的人進不了後台，給按鈕只會把他彈回來
  const role = effectiveRoleOf(me.data)

  return (
    <div className="account">
      {role === 'ORG_MEMBER' && <Link to="/org" className="console-link">機構後台</Link>}
      {role === 'ADMIN' && <Link to="/admin" className="console-link">監控中心</Link>}
      <Link to="/me/profile" className="account__me" title={email} aria-label="個人資料">
        <User className="icon" aria-hidden />
      </Link>
      <button type="button" className="account__out" onClick={() => void signOut()}>登出</button>
    </div>
  )
}
