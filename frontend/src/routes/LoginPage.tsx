import { ArrowLeft } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, Navigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../lib/authContext'
import { PLATFORM_NAME, pageTitle } from '../lib/brand'
import { AuthPanel, type JoinRole } from '../components/auth/AuthPanel'
import { Snowfall } from '../components/Snowfall'
import { Spinner } from '../components/ui/Spinner'

/**
 * 全站唯一的登入／註冊頁，認領者、機構、管理員都從這裡進來。
 *
 * 登入後一律回願望牆（或 `?next=` 指定的地方），後台由頁首的按鈕進入。
 * 例外是剛註冊機構帳號的人：直接接著填機構資料，否則他會落在一個沒有機構的空帳號上。
 *
 * 已登入的人直接導回原本要去的地方——這一頁只是通道，不該卡住任何人。
 *
 * 它在 PublicLayout 之外（全螢幕版面、沒有頁首），所以深色背景與飄雪要自己帶。
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

  if (loading) return <div className="rd"><Spinner /></div>
  if (email) {
    return <Navigate to={joinIntent === 'org' ? '/org/apply' : next ?? '/'} replace />
  }

  return (
    <div className="rd">
      <Snowfall />
      <main className="page">
        <div className="auth">
          <div className="auth__brand">
            <span className="auth__title">
              <svg className="auth__spark icon twinkle" viewBox="0 0 48 48" strokeWidth="2.6" aria-hidden>
                <path d="M20 10c1.6 7.6 4.4 10.4 12 12-7.6 1.6-10.4 4.4-12 12-1.6-7.6-4.4-10.4-12-12 7.6-1.6 10.4-4.4 12-12z" />
                <path d="M36 5v8M32 9h8" />
                <circle cx="9" cy="38" r="3" />
              </svg>
              <Link to="/" className="grad-text" style={{ textDecoration: 'none' }}>{PLATFORM_NAME}</Link>
            </span>
            <p>溫暖傳遞，讓每個願望都閃閃發光</p>
          </div>

          <div className="auth-card">
            <AuthPanel initialTab={initialTab} initialRole={initialRole} onJoinIntent={setJoinIntent} />
          </div>

          <Link to="/" className="auth__back">
            <ArrowLeft className="icon" aria-hidden />先看看有哪些願望
          </Link>
        </div>
      </main>
    </div>
  )
}
