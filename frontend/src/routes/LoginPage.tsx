import { useEffect } from 'react'
import { Link, Navigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../lib/authContext'
import { AuthPanel } from '../components/auth/AuthPanel'
import { Snowfall } from '../components/Snowfall'
import { Spinner } from '../components/Feedback'

/**
 * 主網站的登入頁。
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

  useEffect(() => {
    document.title = '登入 — 線上聖誕老公公'
    return () => { document.title = '線上聖誕老公公' }
  }, [])

  if (loading) return <Spinner />
  if (email) return <Navigate to={next ?? '/'} replace />

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
              線上聖誕老公公
            </span>
          </Link>
        </div>

        <div className="glass-card p-8">
          <AuthPanel
            hint="登入後就能認領願望，並追蹤寄送進度。"
            registerHint="建立帳號之後，你就能挑一個孩子的願望來實現。"
          />
        </div>

        <p className="mt-6 text-center text-sm text-slate-400">
          <Link to="/" className="hover:text-white hover:underline">← 先看看有哪些願望</Link>
        </p>
      </div>
    </div>
  )
}
