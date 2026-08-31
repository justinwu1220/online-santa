import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import { useAuth, usingFirebase } from '../../lib/authContext'
import { PLATFORM_NAME } from '../../lib/brand'
import { effectiveRoleOf, useCurrentUser } from '../../lib/useCurrentUser'
import { EmailVerificationBanner } from '../EmailVerificationBanner'
import { Snowfall } from '../Snowfall'
import { Button } from '../Form'

/**
 * 主網站。
 *
 * 願望牆對所有人公開——人要先看見孩子的願望才會考慮註冊，把牆擋在登入後會直接
 * 殺掉轉換率。認領與查看紀錄才需要登入。
 *
 * 機構入口放在頁尾，不放主導覽：捐贈者是主要對象，機構是少數但知道自己要找什麼。
 * 監控中心完全不出現在這裡。
 *
 * 視覺上是深色玻璃質感（見 docs/DESIGN.md）。深色底鋪在 fixed 圖層而不是改 body，
 * 因為機構後台與監控中心共用同一個 body，那兩個是工作介面要維持亮色。
 */
export function PublicLayout() {
  const { email } = useAuth()

  return (
    <div className="theme-night flex min-h-screen flex-col font-rounded text-white">
      <div className="night-backdrop" />
      <Snowfall />

      <header className="border-b border-white/10 bg-white/5 backdrop-blur-md">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-4 py-3">
          <Link to="/" className="text-lg font-bold tracking-wide">
            <span aria-hidden>🎄</span>{' '}
            <span className="bg-gradient-to-r from-red-300 via-white to-emerald-300
              bg-clip-text text-transparent">
              {PLATFORM_NAME}
            </span>
          </Link>

          <nav className="flex flex-wrap gap-1">
            <NavLink to="/" end className={navLinkClass}>願望牆</NavLink>
            {email && <NavLink to="/me/claims" className={navLinkClass}>我的認領</NavLink>}
          </nav>

          <AuthControls />
        </div>
      </header>

      {!usingFirebase && <DevModeBanner />}
      <EmailVerificationBanner />

      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-10">
        <Outlet />
      </main>

      <footer className="border-t border-white/10 bg-white/5 backdrop-blur-md">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-4 py-6
          text-sm text-slate-400">
          <p>願望屬於孩子，故事屬於每一個願意伸手的人。</p>
          {/* 指向 /org/login 而非直接進註冊頁：那一頁未登入時是「登入／申請」的
              選擇畫面，兩種人都有路走。直接送去註冊頁會讓回頭的機構找不到登入 */}
          <Link to="/org/login" className="text-emerald-300 hover:underline">
            我是兒童機構 →
          </Link>
        </div>
      </footer>
    </div>
  )
}

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  `rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
    isActive ? 'bg-white/10 text-white' : 'text-slate-300 hover:bg-white/5 hover:text-white'
  }`

function AuthControls() {
  const { email, loading, signOut } = useAuth()
  const me = useCurrentUser()
  const location = useLocation()

  if (loading) {
    return <span className="text-sm text-slate-400">載入中…</span>
  }

  if (email) {
    return (
      <div className="flex items-center gap-3">
        {/* 機構成員誤入主網站時，給他一條回後台的路。用生效角色判斷——
            信箱還沒驗證的人進不了後台，這時給連結只會把他彈回登入頁 */}
        {effectiveRoleOf(me.data) === 'ORG_MEMBER' && (
          <Link to="/org" className="text-sm text-emerald-300 hover:underline">機構後台</Link>
        )}
        {/* 帳號圖示取代原本直接顯示 email 的做法：點進去是個人檔案頁，
            title 帶 email 讓 hover 還看得到自己是誰 */}
        <Link
          to="/me/profile"
          title={email}
          aria-label="個人資料"
          className="flex h-9 w-9 items-center justify-center rounded-full border
            border-white/10 bg-white/5 text-slate-300 backdrop-blur-md transition-colors
            hover:border-white/20 hover:bg-white/10 hover:text-white"
        >
          <AccountIcon />
        </Link>
        <Button variant="ghost" className="text-slate-300 hover:bg-white/10 hover:text-white"
          onClick={() => void signOut()}>
          登出
        </Button>
      </div>
    )
  }

  // 帶上目前位置，登入後回得來——分享出去的願望連結點進來時特別重要
  const next = encodeURIComponent(location.pathname + location.search)

  return (
    // 玻璃質感而非實心漸層：頁首整條是半透明的，一顆飽和的按鈕會從那層玻璃上浮出來。
    // 綠色而非紅色——與站內的主要按鈕一致，紅色在這套配色裡只代表危險與逾期
    <Link
      to={`/login?next=${next}`}
      className="rounded-xl border border-emerald-400/30 bg-emerald-500/15 px-4 py-2 text-sm
        font-bold text-emerald-100 backdrop-blur-md transition-colors
        hover:border-emerald-400/50 hover:bg-emerald-500/25 hover:text-white"
    >
      登入 / 註冊
    </Link>
  )
}

function AccountIcon() {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth={1.75} strokeLinecap="round" strokeLinejoin="round"
      className="h-5 w-5" aria-hidden>
      <circle cx="12" cy="8" r="4" />
      <path d="M4 20c0-4 3.5-6 8-6s8 2 8 6" />
    </svg>
  )
}

function DevModeBanner() {
  return (
    <div className="bg-amber-500/15 px-4 py-2 text-center text-xs text-amber-200">
      開發模式：未設定 Firebase，身分僅由 <code>X-Dev-User-Email</code> 標頭指定，沒有任何驗證。
    </div>
  )
}
