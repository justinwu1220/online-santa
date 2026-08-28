import { useEffect, useState } from 'react'
import { Navigate, useSearchParams } from 'react-router-dom'
import { useAuth, usingFirebase } from '../../lib/authContext'
import { useCurrentUser } from '../../lib/useCurrentUser'
import { Button, Field, TextInput } from '../../components/Form'
import { ErrorBanner, Notice, Spinner } from '../../components/Feedback'

/**
 * 監控中心的登入頁。
 *
 * 沒有任何地方連到這裡，也不提供回主網站的連結——這個入口只給知道網址的人。
 * 頁面本身刻意不透露任何系統資訊。
 */
export function AdminLogin() {
  const { email, loading, signIn } = useAuth()
  const me = useCurrentUser()
  const [searchParams] = useSearchParams()
  const [draft, setDraft] = useState('')

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
    if (me.data?.role === 'ADMIN') {
      return <Navigate to={searchParams.get('next') ?? '/admin'} replace />
    }
    return (
      <Shell>
        <Notice tone="warning">
          <p className="font-medium">此帳號沒有存取權限</p>
          <p className="mt-1">請以平台管理員的帳號登入。</p>
        </Notice>
      </Shell>
    )
  }

  return (
    <Shell>
      {usingFirebase ? (
        <Button className="w-full py-3" onClick={() => void signIn()}>
          使用 Google 登入
        </Button>
      ) : (
        <form
          className="space-y-3"
          onSubmit={(event) => { event.preventDefault(); void signIn(draft) }}
        >
          <Field label="管理員帳號" required>
            <TextInput type="email" required value={draft}
              onChange={(event) => setDraft(event.target.value)} />
          </Field>
          <Button type="submit" className="w-full py-2.5">登入</Button>
        </form>
      )}
    </Shell>
  )
}

function Shell({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-900 px-4">
      <div className="w-full max-w-sm rounded-xl bg-white p-8 shadow-lg">
        <h1 className="mb-6 text-xl font-bold text-slate-800">監控中心</h1>
        {children}
      </div>
    </div>
  )
}
