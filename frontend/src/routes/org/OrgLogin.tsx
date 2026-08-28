import { useEffect } from 'react'
import { Link, Navigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../../lib/authContext'
import { useCurrentUser } from '../../lib/useCurrentUser'
import { AuthPanel } from '../../components/auth/AuthPanel'
import { ErrorBanner, Notice, Spinner } from '../../components/Feedback'

/**
 * 機構入口。
 *
 * 這個頁面同時是「登入」與「註冊」的入口——新機構也是從這裡進來的。登入後依角色分流：
 * 已是機構成員直接進後台，一般民眾看到申請表單，管理員擋下。
 *
 * 技術上這跟主網站是同一套 Firebase 帳號，只是一個不同的著陸頁。文案要避免讓機構
 * 誤以為這是獨立的帳號體系。
 */
export function OrgLogin() {
  const { email, loading } = useAuth()
  const me = useCurrentUser()
  const [searchParams] = useSearchParams()

  const next = searchParams.get('next')

  useEffect(() => {
    document.title = '機構登入 — 線上聖誕老公公'
    return () => { document.title = '線上聖誕老公公' }
  }, [])

  if (loading) return <Spinner />

  if (email) {
    if (me.isLoading) return <Spinner label="確認身分" />
    if (me.isError) {
      return <div className="mx-auto max-w-lg pt-16">
        <ErrorBanner error={me.error} onRetry={() => void me.refetch()} />
      </div>
    }

    // 已是機構成員：直接進後台
    if (me.data?.role === 'ORG_MEMBER') {
      return <Navigate to={next ?? '/org'} replace />
    }
    // 一般民眾：引導去申請
    if (me.data?.role === 'DONOR') {
      return <Navigate to="/org/register" replace />
    }
    // 管理員不能兼任機構
    return (
      <Shell>
        <Notice tone="warning">
          <p className="font-medium">平台管理員無法註冊或管理機構</p>
          <p className="mt-1">這是為了避免球員兼裁判。請改用其他帳號登入。</p>
        </Notice>
      </Shell>
    )
  }

  return (
    <Shell>
      <p className="mb-4 text-sm text-slate-600">
        還沒有合作機構帳號？註冊並登入之後就能直接提出申請。
      </p>

      <div className="mt-6">
        <AuthPanel
          hint="登入後即可上架孩子的願望、管理認領與寄送進度。"
          registerHint="建立帳號之後，下一步就能提出合作機構的申請。"
        />
      </div>

      <p className="mt-6 text-center text-sm text-slate-500">
        <Link to="/" className="hover:underline">← 回到願望牆</Link>
      </p>
    </Shell>
  )
}

function Shell({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
      <div className="w-full max-w-md rounded-xl bg-white p-8 shadow-sm ring-1 ring-slate-200">
        <div className="mb-6">
          <p className="text-sm font-medium text-santa-600">線上聖誕老公公</p>
          <h1 className="mt-1 text-2xl font-bold text-slate-800">機構後台</h1>
        </div>
        {children}
      </div>
    </div>
  )
}
