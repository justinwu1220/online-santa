import { useQuery } from '@tanstack/react-query'
import { Menu, TreePine, User, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import { api } from '../../lib/api'
import { useAuth, usingLocalAuth } from '../../lib/authContext'
import { CAMPAIGN_YEAR, PLATFORM_NAME, SUPPORT_EMAIL } from '../../lib/brand'
import { useCurrentCampaign } from '../../lib/campaign'
import type { ClaimDonorView, PageResponse } from '../../lib/types'
import { useMyProfile } from '../../lib/useMyProfile'
import { effectiveRoleOf, useCurrentUser } from '../../lib/useCurrentUser'
import { OnboardHost } from '../onboard/OnboardHost'
import { Snowfall } from '../Snowfall'
import { TermsGate } from '../terms/TermsGate'
import { TransitionProvider } from '../transition/TransitionProvider'
import { ToastProvider } from '../ui/Toast'
import { SuspendedBar } from '../ui/SuspendedBar'
import { VerifyBar } from '../ui/VerifyBar'

/**
 * 主網站。
 *
 * 願望牆對所有人公開——人要先看見孩子的願望才會考慮註冊，把牆擋在登入後會直接
 * 殺掉轉換率。認領與查看紀錄才需要登入。
 *
 * 機構從頁首的「登入 / 註冊」進入，註冊時選擇機構身分；登入後機構與管理員的後台按鈕
 * 出現在右上角。頁尾只有三件法務上該露出的資訊（聯絡信箱、勸募許可文號、著作權），
 * 不放導覽連結。
 *
 * 頁首與雪花放在路由之外，換頁時不重新產生（SPEC 6.1 防閃爍的條件之一）。
 */
export function PublicLayout() {
  return (
    <div className="rd">
      <Snowfall />
      <ToastProvider>
        <OnboardHost>
        <TransitionProvider>
          <SiteHeader />
          {!usingLocalAuth && (
            <div className="bar bar--dev">
              開發模式（<code>VITE_DEV_AUTH=true</code>）：身分僅由 <code>X-Dev-User-Email</code> 標頭指定，沒有任何驗證。
            </div>
          )}
          <VerifyBar />
          <SuspendedBar />
          <TermsGate />
          <main className="page">
            <Outlet />
          </main>
          <SiteFooter />
        </TransitionProvider>
        </OnboardHost>
      </ToastProvider>
    </div>
  )
}

/**
 * 頁尾。願望牆捲到底才看得到，內容是三件該公開揭露的事：聯絡信箱、勸募許可文號、著作權。
 *
 * 信箱<strong>不做成連結</strong>：在手機上點到 mailto 會跳出選擇郵件 App 的系統對話框，
 * 多數人其實只是想把地址複製走。
 *
 * 勸募許可文號由管理員維護在活動檔期裡，還沒有檔期（或抓不到）時整行不顯示——
 * 不確定的法定文號寧可不寫。
 */
function SiteFooter() {
  const campaign = useCurrentCampaign()

  return (
    <footer className="site-footer">
      <div className="site-footer__in">
        <p className="site-footer__contact">聯絡我們：{SUPPORT_EMAIL}</p>
        {campaign.data?.permitNo && (
          <p className="site-footer__permit">勸募許可文號：{campaign.data.permitNo}</p>
        )}
        <p className="site-footer__copy">© {CAMPAIGN_YEAR} {PLATFORM_NAME}</p>
      </div>
    </footer>
  )
}

/**
 * 頁首。桌機是一列：標誌、主選單、帳號區。
 *
 * 手機（≤520px）放不下那一整列，所以只留標誌與右邊的三條線，其餘收進 `.site-header__menu`
 * ——同一份 DOM，桌機用 `display: contents` 讓它在版面上消失，nav 與帳號區照舊是頁首的
 * 直接子元素，不必為兩種版面各寫一份。
 */
function SiteHeader() {
  const { email } = useAuth()
  const [menuOpen, setMenuOpen] = useState(false)
  const location = useLocation()

  // 換頁就收起來（上一頁、下一頁也算）。render 期間依路徑變化調整，不用 effect
  const path = location.pathname + location.search
  const [lastPath, setLastPath] = useState(path)
  if (path !== lastPath) {
    setLastPath(path)
    if (menuOpen) setMenuOpen(false)
  }

  useEffect(() => {
    if (!menuOpen) return
    const onKey = (event: KeyboardEvent) => { if (event.key === 'Escape') setMenuOpen(false) }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [menuOpen])

  return (
    <header className="site-header">
      <div className="site-header__in">
        <Link to="/" className="brand">
          <TreePine className="icon" size={22} aria-hidden style={{ color: '#4ade80' }} />
          <span className="brand__text">{PLATFORM_NAME}</span>
        </Link>

        <button
          type="button"
          className="menu-btn"
          aria-label={menuOpen ? '關閉選單' : '開啟選單'}
          aria-expanded={menuOpen}
          aria-controls="site-menu"
          onClick={() => setMenuOpen((open) => !open)}
        >
          {menuOpen ? <X className="icon" aria-hidden /> : <Menu className="icon" aria-hidden />}
        </button>

        {/*
          點到選單裡的任何連結或按鈕就收起來。只靠換頁的 effect 不夠：已經在願望牆時再點
          一次「願望牆」，路徑沒變，effect 不會跑，選單會留在畫面上。
        */}
        <div
          className="site-header__menu"
          id="site-menu"
          data-open={menuOpen || undefined}
          onClick={(event) => {
            if ((event.target as HTMLElement).closest('a, button')) setMenuOpen(false)
          }}
        >
          <nav className="nav" aria-label="主選單">
            <NavLink to="/" end className="nav-link">願望牆</NavLink>
            {email && <MyClaimsLink />}
          </nav>

          <AuthControls />
        </div>
      </div>

      {/* 點選單以外的地方收起來。用按鈕而不是 div，鍵盤與讀螢幕軟體也有一個明確的關閉方式 */}
      {menuOpen && (
        <button type="button" className="menu-scrim" aria-label="關閉選單" onClick={() => setMenuOpen(false)} />
      )}
    </header>
  )
}

/** 「我的認領」與進行中的認領數（取消、收回的不算）。 */
function MyClaimsLink() {
  const me = useCurrentUser()
  const role = effectiveRoleOf(me.data)
  const isDonor = role === 'DONOR'

  const claims = useQuery({
    queryKey: ['claims', 'me', 'count'],
    queryFn: () => api.get<PageResponse<ClaimDonorView>>('/api/claims/me?size=100'),
    enabled: isDonor,
    staleTime: 30_000,
  })

  const active = claims.data?.content
    .filter((claim) => claim.status !== 'RELEASED' && claim.status !== 'CANCELLED').length ?? 0

  // 機構成員與管理員沒有認領（路由只給認領者），不顯示這個連結
  if (role === 'ORG_MEMBER' || role === 'ADMIN') return null

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
  const name = useMyProfile().data?.displayName
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
      {role === 'ADMIN' && <Link to="/admin" className="console-link">管理員後台</Link>}
      {/* 個人資訊只給認領者；機構成員與管理員的在各自的後台，公開頁不顯示 */}
      {role === 'DONOR' && name && <span className="account__name" title={name}>{name}</span>}
      {role === 'DONOR' && (
        <Link to="/me/profile" className="account__me" title={email} aria-label="個人資料">
          <User className="icon" aria-hidden />
        </Link>
      )}
      <button type="button" className="account__out" onClick={() => void signOut()}>登出</button>
    </div>
  )
}
