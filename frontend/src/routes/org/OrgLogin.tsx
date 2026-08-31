import { useEffect, useState } from 'react'
import { Link, Navigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../../lib/authContext'
import { effectiveRoleOf, useCurrentUser } from '../../lib/useCurrentUser'
import { AuthPanel } from '../../components/auth/AuthPanel'
import { WrongAccountPanel } from '../../components/auth/WrongAccountPanel'
import { ErrorBanner, Spinner } from '../../components/Feedback'

/**
 * 機構入口。
 *
 * 未登入時先讓人選擇要「登入後台」還是「申請成為合作機構」——這兩種人的需求完全
 * 不同，把他們丟進同一個登入面板，回頭的機構會找不到登入、新機構會不知道要先註冊。
 *
 * 例外：帶著 `?next=` 進來的人是被 RequireRole 從某個後台頁面導過來的，他要的就是
 * 登入，這時跳過選擇畫面直接給表單。
 *
 * 登入後依角色分流：已是機構成員直接進後台，一般民眾導去申請頁，管理員擋下。
 *
 * 技術上這跟主網站是同一套 Firebase 帳號，只是一個不同的著陸頁。文案要避免讓機構
 * 誤以為這是獨立的帳號體系。
 */
export function OrgLogin() {
  const { email, loading } = useAuth()
  const me = useCurrentUser()
  const [searchParams] = useSearchParams()

  const next = searchParams.get('next')
  const [view, setView] = useState<'choose' | 'signIn'>(next ? 'signIn' : 'choose')

  useEffect(() => {
    document.title = (view === 'choose' ? '機構入口' : '機構登入') + ' — 線上聖誕老公公'
    return () => { document.title = '線上聖誕老公公' }
  }, [view])

  if (loading) return <Spinner />

  if (email) {
    if (me.isLoading) return <Spinner label="確認身分" />
    if (me.isError) {
      return <div className="mx-auto max-w-lg pt-16">
        <ErrorBanner error={me.error} onRetry={() => void me.refetch()} />
      </div>
    }

    const role = effectiveRoleOf(me.data)

    // 已是機構成員：直接進後台
    if (role === 'ORG_MEMBER') {
      return <Navigate to={next ?? '/org'} replace />
    }
    // 一般民眾：引導去申請。信箱未驗證的人會落在這裡，
    // 申請頁本來就會擋下並要求先驗證，所以不必在這一層重複
    if (role === 'DONOR') {
      return <Navigate to="/org/register" replace />
    }
    // 管理員不能兼任機構
    return (
      <Shell title="機構後台">
        <WrongAccountPanel
          expected="ORG_MEMBER"
          reason="平台管理員無法註冊或管理機構，這是為了避免球員兼裁判。"
        />
      </Shell>
    )
  }

  if (view === 'choose') {
    return (
      <Shell title="機構入口">
        <div className="space-y-3">
          <ChoiceCard
            title="登入機構後台"
            description="已經是合作機構，要管理願望與寄送進度"
            onClick={() => setView('signIn')}
          />
          <ChoiceCard
            title="申請成為合作機構"
            description="還沒有帳號，要提出合作申請"
            to="/org/register"
          />
        </div>

        <BackToWall />
      </Shell>
    )
  }

  return (
    <Shell title="機構後台">
      {/* 註冊在這裡沒有意義：機構帳號要連同機構資料一起申請，那是 /org/register
          的一頁到底表單。留一個註冊分頁只會產生一個沒有機構的空帳號 */}
      <AuthPanel
        allowRegister={false}
        hint="登入後即可上架孩子的願望、管理認領與寄送進度。"
      />

      <button type="button" onClick={() => setView('choose')}
        className="mt-6 w-full text-sm text-slate-500 hover:underline">
        ← 返回
      </button>

      <BackToWall />
    </Shell>
  )
}

function ChoiceCard({ title, description, to, onClick }: {
  title: string
  description: string
  /** 二選一：連到別的路由，或就地切換畫面 */
  to?: string
  onClick?: () => void
}) {
  const content = (
    <>
      <span className="flex items-center justify-between gap-3">
        <span className="font-medium text-slate-800">{title}</span>
        <span aria-hidden className="text-santa-600">→</span>
      </span>
      <span className="mt-1 block text-sm text-slate-500">{description}</span>
    </>
  )
  const className = 'block w-full rounded-lg border border-slate-200 px-4 py-4 text-left '
    + 'transition-colors hover:border-santa-300 hover:bg-santa-50'

  return to
    ? <Link to={to} className={className}>{content}</Link>
    : <button type="button" onClick={onClick} className={className}>{content}</button>
}

function BackToWall() {
  return (
    <p className="mt-6 text-center text-sm text-slate-500">
      <Link to="/" className="hover:underline">← 回到願望牆</Link>
    </p>
  )
}

function Shell({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
      <div className="w-full max-w-md rounded-xl bg-white p-8 shadow-sm ring-1 ring-slate-200">
        <div className="mb-6">
          <p className="text-sm font-medium text-santa-600">線上聖誕老公公</p>
          <h1 className="mt-1 text-2xl font-bold text-slate-800">{title}</h1>
        </div>
        {children}
      </div>
    </div>
  )
}
