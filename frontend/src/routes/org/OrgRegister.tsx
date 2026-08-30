import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { ApiError, api } from '../../lib/api'
import { useAuth } from '../../lib/authContext'
import type { OrganizationView } from '../../lib/types'
import { EmailVerificationBanner } from '../../components/EmailVerificationBanner'
import { ErrorBanner, Notice } from '../../components/Feedback'
import { Button, Field, TextArea, TextInput } from '../../components/Form'

/** 機構自助註冊。送出後為待審核，須經平台管理員核准才能上架願望。 */
export function OrgRegister() {
  const { emailVerified } = useAuth()
  const queryClient = useQueryClient()
  const [form, setForm] = useState({
    name: '', contactEmail: '', contactPhone: '', address: '', description: '',
  })

  const register = useMutation({
    mutationFn: () => api.post<OrganizationView>('/api/organizations', form),
    onSuccess: () => {
      // 註冊者會從 DONOR 變成 ORG_MEMBER，身分要重新載入
      void queryClient.invalidateQueries({ queryKey: ['me'] })
      void queryClient.invalidateQueries({ queryKey: ['organization'] })
    },
  })

  const update = (key: keyof typeof form) => (
    event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>,
  ) => setForm((current) => ({ ...current, [key]: event.target.value }))

  const fieldErrors = register.error instanceof ApiError ? register.error.fieldErrors : undefined

  return (
    <>
      {/*
        這一頁不在任何 layout 底下（App.tsx 的 org/register 是獨立路由），橫幅要自己帶。
        少了它，未驗證的人會看到停用的送出鈕卻沒有任何重寄或更新狀態的入口——而且
        已經點過驗證信的人也會卡住，因為更新 token 的按鈕正是橫幅上那一顆。
      */}
      <EmailVerificationBanner />

      <section className="mx-auto max-w-2xl space-y-6 px-4 py-10">
        <div>
          <h1 className="text-3xl font-bold text-santa-700">機構註冊</h1>
          <p className="mt-2 text-slate-600">
            註冊後平台會審核你的機構資料，核准後即可上架孩子的願望。
          </p>
        </div>

        {!emailVerified && (
          <Notice tone="warning">
            <p className="font-medium">請先完成信箱驗證</p>
            <p className="mt-1">
              機構申請通過後就能上架孩童資料，門檻不能只是「填了一個信箱」。
              上方的橫幅可以重新寄送驗證信。
            </p>
          </Notice>
        )}

        <Notice tone="warning">
          成為機構成員後，<strong>這個帳號將無法再以個人身分認領願望</strong>——
          一個帳號只能有一種身分。如果你也想以個人身分參與，建議機構改用另一個
          聯絡信箱註冊。
        </Notice>

        <form
          className="space-y-5"
          onSubmit={(event) => { event.preventDefault(); register.mutate() }}
        >
          <Field label="機構名稱" required error={fieldErrors?.name}>
            <TextInput required maxLength={120} value={form.name} onChange={update('name')} />
          </Field>
          <Field label="聯絡信箱" required error={fieldErrors?.contactEmail}>
            <TextInput required type="email" maxLength={255}
              value={form.contactEmail} onChange={update('contactEmail')} />
          </Field>
          <div className="grid gap-5 sm:grid-cols-2">
            <Field label="聯絡電話">
              <TextInput maxLength={40} value={form.contactPhone} onChange={update('contactPhone')} />
            </Field>
            <Field label="地址">
              <TextInput maxLength={255} value={form.address} onChange={update('address')} />
            </Field>
          </div>
          <Field label="機構簡介" hint="讓捐贈者了解你們服務的對象">
            <TextArea rows={4} maxLength={2000}
              value={form.description} onChange={update('description')} />
          </Field>

          {register.isError && <ErrorBanner error={register.error} />}

          <Button type="submit" disabled={register.isPending || !emailVerified}>
            {register.isPending ? '送出中…' : '送出註冊申請'}
          </Button>
        </form>
      </section>
    </>
  )
}
