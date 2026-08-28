import { useEffect } from 'react'
import { Link, Navigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../lib/authContext'
import { AuthPanel } from '../components/auth/AuthPanel'
import { Spinner } from '../components/Feedback'

/**
 * 主網站的登入頁。
 *
 * 已登入的人直接導回原本要去的地方——這一頁只是通道，不該卡住任何人。
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
    <div className="flex min-h-screen items-center justify-center bg-santa-50 px-4 py-10">
      <div className="w-full max-w-md">
        <div className="mb-6 text-center">
          <Link to="/" className="text-lg font-bold text-santa-700">
            🎄 線上聖誕老公公
          </Link>
        </div>

        <div className="rounded-xl bg-white p-8 shadow-sm ring-1 ring-santa-100">
          <AuthPanel
            hint="登入後就能認領願望，並追蹤寄送進度。"
            registerHint="建立帳號之後，你就能挑一個孩子的願望來實現。"
          />
        </div>

        <p className="mt-6 text-center text-sm text-slate-500">
          <Link to="/" className="hover:underline">← 先看看有哪些願望</Link>
        </p>
      </div>
    </div>
  )
}
