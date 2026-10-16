import { useEffect, useState } from 'react'
import { Link, Navigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../lib/authContext'
import { PLATFORM_NAME, pageTitle } from '../lib/brand'
import { AuthPanel, type JoinRole } from '../components/auth/AuthPanel'
import { Snowfall } from '../components/Snowfall'
import { Spinner } from '../components/Feedback'

/**
 * 全站唯一的登入／註冊頁，認領者、機構、管理員都從這裡進來。
 *
 * 登入後一律回願望牆（或 `?next=` 指定的地方），後台由頁首的按鈕進入。
 * 例外是剛註冊機構帳號的人：直接接著填機構資料，否則他會落在一個沒有機構的空帳號上。
 *
 * 已登入的人直接導回原本要去的地方——這一頁只是通道，不該卡住任何人。
 *
 * 它在 PublicLayout 之外（全螢幕版面、沒有導覽列），所以深色背景與飄雪要自己帶，
 * 否則從願望牆點過來會突然從深色跳成亮色。
 */
export function LoginPage() {
  const { email, loading } = useAuth()
  const [searchParams] = useSearchParams()
  const next = searchParams.get('next')
  const initialTab = searchParams.get('tab') === 'register' ? 'register' : 'signIn'
  const initialRole: JoinRole | null = initialTab === 'register' && searchParams.get('as') === 'org'
    ? 'org' : null
  // 在送出動作之前就設定好（見 AuthPanel）：登入一成功頁面就會重新 render 並導走
  const [joinIntent, setJoinIntent] = useState<JoinRole | null>(null)

  useEffect(() => {
    document.title = pageTitle('登入')
    return () => { document.title = pageTitle() }
  }, [])

  if (loading) return <Spinner />
  if (email) {
    return <Navigate to={joinIntent === 'org' ? '/org/apply' : next ?? '/'} replace />
  }

  return (
    <div className="theme-night flex min-h-screen items-center justify-center px-4 py-10
      font-rounded text-white">
      <div className="night-backdrop" />
      <Snowfall />

      <div className="w-full max-w-md">
        <div className="mb-6 text-center">
          <Link to="/" className="text-lg font-bold tracking-wide">
            <span aria-hidden>🎄</span>{' '}
            <span className="bg-gradient-to-r from-red-300 via-white to-emerald-300
              bg-clip-text text-transparent">
              {PLATFORM_NAME}
            </span>
          </Link>
        </div>

        <div className="glass-card p-8">
          <AuthPanel
            initialTab={initialTab}
            initialRole={initialRole}
            onJoinIntent={setJoinIntent}
          />
        </div>

        <p className="mt-6 text-center text-sm text-slate-400">
          <Link to="/" className="hover:text-white hover:underline">← 先看看有哪些願望</Link>
        </p>
      </div>
    </div>
  )
}
