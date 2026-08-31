import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { ApiError, api } from '../../lib/api'
import { useAuth } from '../../lib/authContext'
import { effectiveRoleOf, useCurrentUser } from '../../lib/useCurrentUser'
import type { OrganizationView } from '../../lib/types'
import { EmailVerificationBanner } from '../../components/EmailVerificationBanner'
import { WrongAccountPanel } from '../../components/auth/WrongAccountPanel'
import { ErrorBanner, Notice, Spinner } from '../../components/Feedback'
import { Button, Field, TextArea, TextInput } from '../../components/Form'
import { StepHeading } from './applyShared'

type Form = {
  name: string
  contactEmail: string
  contactPhone: string
  address: string
  description: string
}

type Draft = { form: Form; contactEmailTouched: boolean }

const EMPTY: Draft = {
  form: { name: '', contactEmail: '', contactPhone: '', address: '', description: '' },
  contactEmailTouched: false,
}

const draftKeyFor = (email: string) => `org-apply-draft:${email}`

function readDraft(key: string): Draft {
  try {
    const saved = sessionStorage.getItem(key)
    if (!saved) return EMPTY
    const parsed = JSON.parse(saved) as Partial<Draft>
    return {
      form: { ...EMPTY.form, ...parsed.form },
      contactEmailTouched: Boolean(parsed.contactEmailTouched),
    }
  } catch {
    // 讀不到或內容壞掉就當作沒有草稿——不要讓它變成白畫面
    return EMPTY
  }
}

/**
 * 機構申請的步驟二：機構資料。
 *
 * <p>這一層只做身分分流。表單在 ApplyForm 裡，並以帳號信箱當 key——換帳號時整個重新
 * 掛載，草稿才不會跨帳號互相汙染。
 */
export function OrgApply() {
  const auth = useAuth()
  const me = useCurrentUser()

  if (auth.loading) return <Spinner />
  // 還沒有帳號：回步驟一
  if (!auth.email) return <Navigate to="/org/register" replace />

  if (me.isLoading) return <Spinner label="確認身分" />
  if (me.isError) {
    return (
      <Shell>
        <ErrorBanner error={me.error} onRetry={() => void me.refetch()} />
      </Shell>
    )
  }

  const role = effectiveRoleOf(me.data)
  if (role === 'ORG_MEMBER') return <Navigate to="/org" replace />
  if (role === 'ADMIN') {
    return (
      <Shell>
        <WrongAccountPanel
          expected="ORG_MEMBER"
          reason="平台管理員無法註冊或管理機構，這是為了避免球員兼裁判。"
        />
      </Shell>
    )
  }

  return <ApplyForm key={auth.email} email={auth.email} />
}

/**
 * 機構資料表單。
 *
 * <p>密碼註冊的人會在這一頁等驗證信，而在同一個分頁點驗證信連結會離開頁面——所以內容
 * 存進 sessionStorage，回來時還原。這是拆成兩頁唯一的實質代價，不處理的話使用者會白
 * 打一次整張表單。
 */
function ApplyForm({ email }: { email: string }) {
  const auth = useAuth()
  const queryClient = useQueryClient()

  const draftKey = draftKeyFor(email)
  // 在初始化函式裡讀，而不是在 effect 裡 setState——後者會多一次 render，
  // 而且第一幀會閃過空白的表單
  const [draft, setDraft] = useState<Draft>(() => readDraft(draftKey))
  const { form, contactEmailTouched } = draft

  useEffect(() => {
    try {
      sessionStorage.setItem(draftKey, JSON.stringify(draft))
    } catch {
      // 存不了就算了，只是重新整理後要重打
    }
  }, [draft, draftKey])

  const contactEmail = contactEmailTouched ? form.contactEmail : email

  const register = useMutation({
    mutationFn: () => api.post<OrganizationView>('/api/organizations', { ...form, contactEmail }),
    onSuccess: () => {
      try {
        sessionStorage.removeItem(draftKey)
      } catch { /* 清不掉也無所謂，下次進來會被覆寫 */ }
      // 註冊者會從 DONOR 變成 ORG_MEMBER，身分要重新載入。
      //
      // 這裡刻意不自己 navigate('/org')：身分重新載入是非同步的，馬上導過去的話
      // /org 的 RequireRole 讀到的還是舊的 DONOR，會把人彈回登入頁再彈回來。
      // 上層的角色分流看到 ORG_MEMBER 就會自動導向，等身分真的更新了才走。
      void queryClient.invalidateQueries({ queryKey: ['me'] })
      void queryClient.invalidateQueries({ queryKey: ['organization'] })
    },
  })

  const update = (key: keyof Form) => (
    event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>,
  ) => setDraft((current) => ({
    ...current,
    form: { ...current.form, [key]: event.target.value },
  }))

  const fieldErrors = register.error instanceof ApiError ? register.error.fieldErrors : undefined

  if (register.isSuccess) {
    return <Shell><Spinner label="申請已送出，正在進入機構後台" /></Shell>
  }

  return (
    <>
      {/* 這一頁不在任何 layout 底下，橫幅要自己帶——密碼註冊的人正是在這裡等驗證信，
          而「我已經驗證好了」那顆按鈕就在橫幅上 */}
      <EmailVerificationBanner />

      <Shell>
        <StepHeading step={2} title="機構資料" />

        <p className="text-sm text-slate-600">
          以 <strong>{email}</strong> 的身分申請。送出後由平台審核，核准前可以先建立
          願望草稿。
        </p>

        <Notice tone="warning">
          成為機構成員後，<strong>這個帳號將無法再以個人身分認領願望</strong>——
          一個帳號只能有一種身分。如果你也想以個人身分參與，建議機構改用另一個
          聯絡信箱註冊。
        </Notice>

        <form
          className="space-y-5"
          onSubmit={(event) => {
            event.preventDefault()
            if (!auth.emailVerified) return
            register.mutate()
          }}
        >
          <Field label="機構名稱" required error={fieldErrors?.name}>
            <TextInput required maxLength={120} placeholder="某某社會福利基金會"
              value={form.name} onChange={update('name')} />
          </Field>

          <Field label="聯絡信箱" required error={fieldErrors?.contactEmail}
            hint={contactEmailTouched ? undefined : '預設與帳號信箱相同，可以改'}>
            <TextInput required type="email" maxLength={255} value={contactEmail}
              onChange={(event) => setDraft((current) => ({
                contactEmailTouched: true,
                form: { ...current.form, contactEmail: event.target.value },
              }))} />
          </Field>

          {/* 電話與地址必填：捐贈者認領之後會在認領詳情頁看到它們，並照著寄送 */}
          <Field label="聯絡電話" required error={fieldErrors?.contactPhone}>
            <TextInput required maxLength={40} placeholder="02-1234-5678"
              value={form.contactPhone} onChange={update('contactPhone')} />
          </Field>

          <Field label="收件地址" required error={fieldErrors?.address}
            hint="捐贈者會把禮物寄到這裡，請填寫完整地址">
            <TextInput required maxLength={255} placeholder="台北市中正區某某路 1 號"
              value={form.address} onChange={update('address')} />
          </Field>

          <Field label="機構簡介" hint="讓捐贈者了解你們服務的對象"
            error={fieldErrors?.description}>
            <TextArea rows={4} maxLength={2000}
              value={form.description} onChange={update('description')} />
          </Field>

          {!auth.emailVerified && (
            <Notice tone="warning">
              <p className="font-medium">送出前要先完成信箱驗證</p>
              <p className="mt-1">
                機構申請通過後就能上架孩童資料，門檻不能只是「填了一個信箱」。
                驗證信寄到 <strong>{email}</strong>，點完連結後回到這一頁，按上方橫幅的
                「我已經驗證好了」就能送出。
                <strong>你填的資料會留著，離開這一頁也不會消失。</strong>
              </p>
            </Notice>
          )}

          {register.isError && <ErrorBanner error={register.error} />}

          <Button type="submit" className="w-full py-2.5"
            disabled={register.isPending || !auth.emailVerified}>
            {register.isPending ? '送出中…' : '送出申請'}
          </Button>
        </form>
      </Shell>
    </>
  )
}

function Shell({ children }: { children: React.ReactNode }) {
  return <div className="mx-auto max-w-2xl space-y-6 px-4 py-10">{children}</div>
}
