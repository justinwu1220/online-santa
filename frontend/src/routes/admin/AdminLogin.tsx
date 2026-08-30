import { useEffect } from 'react'
import { Navigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../../lib/authContext'
import { effectiveRoleOf, useCurrentUser } from '../../lib/useCurrentUser'
import { AuthPanel } from '../../components/auth/AuthPanel'
import { WrongAccountPanel } from '../../components/auth/WrongAccountPanel'
import { ErrorBanner, Spinner } from '../../components/Feedback'

/**
 * 監控中心的登入頁。
 *
 * 沒有任何地方連到這裡，也不提供回主網站的連結——這個入口只給知道網址的人。
 * 頁面本身刻意不透露任何系統資訊。
 */
export function AdminLogin() {
  const { email, loading } = useAuth()
  const me = useCurrentUser()
  const [searchParams] = useSearchParams()

  useEffect(() => {
    document.title = '監控中心'
    return () => { document.title = '線上聖誕老公公' }
  }, [])

  if (loading) return <Spinner />

  if (email) {
    if (me.isLoading) return <Spinner label="確認身分" />
    if (me.isError) {
      return <Shell><ErrorBanner error={me.error} onRetry={() => void me.refetch()} /></Shell>
    }
    if (effectiveRoleOf(me.data) === 'ADMIN') {
      return <Navigate to={searchParams.get('next') ?? '/admin'} replace />
    }
    // 這裡是 RequireRole 的另一端：停下來，不要導回 next，否則會無限重導
    return (
      <Shell>
        <WrongAccountPanel expected="ADMIN" reason="請以平台管理員的帳號登入。" />
      </Shell>
    )
  }

  return (
    <Shell>
      {/* 不提供註冊：管理員身分來自 APP_ADMIN_EMAILS 白名單，不是註冊來的 */}
      <AuthPanel allowRegister={false} />
    </Shell>
  )
}

function Shell({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-900 px-4">
      <div className="w-full max-w-md rounded-xl bg-white p-8 shadow-lg">
        <h1 className="mb-6 text-xl font-bold text-slate-800">監控中心</h1>
        {children}
      </div>
    </div>
  )
}
