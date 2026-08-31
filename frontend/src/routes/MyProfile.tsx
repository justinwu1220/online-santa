import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { ApiError, api } from '../lib/api'
import { describeAuthError, useAuth } from '../lib/authContext'
import { pageTitle } from '../lib/brand'
import { useMyProfile } from '../lib/useMyProfile'
import type { UserProfile } from '../lib/types'
import { Breadcrumb } from '../components/Breadcrumb'
import { ErrorBanner, Notice, Spinner } from '../components/Feedback'
import { Button, Field, TextInput } from '../components/Form'

/**
 * 個人檔案設定頁。
 *
 * 用戶名稱會在認領後以 donorName 顯示給機構（見 ClaimOrgView / AdminClaimView），
 * 這裡的欄位提示要讓使用者先知道這件事，而不是事後才發現。
 */
export function MyProfile() {
  const { email } = useAuth()
  const profile = useMyProfile()

  useEffect(() => {
    document.title = pageTitle('個人資料')
    return () => { document.title = pageTitle() }
  }, [])

  if (!email) {
    return <Notice>請先在右上角登入，才能查看個人資料。</Notice>
  }

  return (
    <section className="max-w-2xl">
      <Breadcrumb items={[{ label: '願望牆', to: '/' }, { label: '個人資料' }]} />

      <h1 className="mt-4 text-3xl font-bold text-white">個人資料</h1>

      <div className="mt-6 space-y-6">
        {profile.isLoading && <Spinner label="載入個人資料" />}
        {profile.isError && (
          <ErrorBanner error={profile.error} onRetry={() => void profile.refetch()} />
        )}

        {profile.data && (
          <>
            <ProfileForm profile={profile.data} />
            <EmailSection profile={profile.data} />
            <SecuritySection email={email} />
          </>
        )}
      </div>
    </section>
  )
}

function ProfileForm({ profile }: { profile: UserProfile }) {
  const queryClient = useQueryClient()
  const [form, setForm] = useState({
    displayName: profile.displayName,
    phone: profile.phone ?? '',
  })

  const save = useMutation({
    mutationFn: () => api.patch<UserProfile>('/api/me/profile', form),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['me', 'profile'] })
      void queryClient.invalidateQueries({ queryKey: ['me'] })
    },
  })

  /**
   * 清掉上一次儲存的結果。
   *
   * 表單一有變動就要清——否則使用者改完東西還看得到綠色的「已儲存」，
   * 會以為新的改動也存進去了。錯誤訊息同理：它描述的是舊的那一次送出。
   */
  const clearSaveResult = () => {
    if (save.isSuccess || save.isError) save.reset()
  }

  const update = (key: keyof typeof form) => (
    event: React.ChangeEvent<HTMLInputElement>,
  ) => {
    setForm((current) => ({ ...current, [key]: event.target.value }))
    clearSaveResult()
  }

  const fieldErrors = save.error instanceof ApiError ? save.error.fieldErrors : undefined

  return (
    <form
      className="glass-card space-y-5 p-6"
      onSubmit={(event) => { event.preventDefault(); save.mutate() }}
    >
      <h2 className="font-semibold text-white">基本資料</h2>

      <Field label="用戶名稱" required error={fieldErrors?.displayName}
        hint="認領願望時，機構端會看到這個名稱">
        <TextInput required maxLength={100}
          value={form.displayName} onChange={update('displayName')} />
      </Field>

      <Field label="連絡電話" error={fieldErrors?.phone}>
        <TextInput maxLength={40} value={form.phone} onChange={update('phone')} />
      </Field>

      {save.isError && <ErrorBanner error={save.error} />}
      {save.isSuccess && <Notice tone="success">已儲存。</Notice>}

      <Button type="submit" disabled={save.isPending}>
        {save.isPending ? '儲存中…' : '儲存'}
      </Button>
    </form>
  )
}

function EmailSection({ profile }: { profile: UserProfile }) {
  const auth = useAuth()
  const [busy, setBusy] = useState(false)
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const resend = async () => {
    setBusy(true)
    setError(null)
    try {
      await auth.resendVerification()
      setSent(true)
    } catch (caught) {
      setError(describeAuthError(caught))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="glass-card space-y-3 p-6">
      <h2 className="font-semibold text-white">Email</h2>

      <div className="flex flex-wrap items-center gap-3">
        <span className="text-sm text-slate-300">{profile.email}</span>
        <span className={'badge inline-flex items-center rounded-full px-2.5 py-0.5 '
          + `text-xs font-medium ${profile.emailVerified
            ? 'badge-positive bg-santa-100 text-santa-700'
            : 'badge-warning bg-rose-100 text-berry-600'}`}>
          {profile.emailVerified ? '已驗證' : '未驗證'}
        </span>
      </div>

      {!profile.emailVerified && (
        <div className="space-y-2">
          <Button variant="secondary" disabled={busy} onClick={() => void resend()}>
            {busy ? '寄送中…' : '重新寄送驗證信'}
          </Button>
          {sent && <Notice tone="success">驗證信已重新寄出，請查看信箱（記得檢查垃圾郵件匣）。</Notice>}
          {error && <Notice tone="warning">{error}</Notice>}
        </div>
      )}
    </div>
  )
}

function SecuritySection({ email }: { email: string }) {
  const auth = useAuth()
  const [busy, setBusy] = useState(false)
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const send = async () => {
    setBusy(true)
    setError(null)
    try {
      await auth.sendPasswordReset(email)
      setSent(true)
    } catch (caught) {
      setError(describeAuthError(caught))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="glass-card space-y-3 p-6">
      <h2 className="font-semibold text-white">帳號安全</h2>

      <Button variant="secondary" disabled={busy} onClick={() => void send()}>
        {busy ? '寄送中…' : '寄送密碼重設信'}
      </Button>
      {sent && <Notice tone="success">密碼重設信已寄出，請查看信箱。</Notice>}
      {error && <Notice tone="warning">{error}</Notice>}
    </div>
  )
}
