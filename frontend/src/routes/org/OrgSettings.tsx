import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../../lib/api'
import type { AttachmentView, OrganizationView, ReleasePolicy } from '../../lib/types'
import { ACCEPTED_ORG_DOCUMENT_TYPES, MAX_ORG_DOCUMENTS, validateOrgDocument } from '../../lib/upload'
import { ErrorBanner, Notice, Spinner } from '../../components/Feedback'
import { Button, Field, TextArea, TextInput } from '../../components/Form'
import { ImageUploader } from '../../components/ImageUploader'
import { useOrgContext } from './orgContext'

export function OrgSettings() {
  const { organization } = useOrgContext()
  const queryClient = useQueryClient()

  const [form, setForm] = useState({
    name: organization.name,
    contactPerson: organization.contactPerson ?? '',
    contactEmail: organization.contactEmail,
    contactPhone: organization.contactPhone ?? '',
    address: organization.address ?? '',
    preferredDeliveryTime: organization.preferredDeliveryTime ?? '',
    description: organization.description ?? '',
    releasePolicy: organization.releasePolicy,
    releaseAfterDays: organization.releaseAfterDays ?? 7,
  })

  const save = useMutation({
    mutationFn: () => api.patch<OrganizationView>('/api/organizations/me', {
      ...form,
      // MANUAL 政策不該帶天數，後端與資料庫的約束都會擋
      releaseAfterDays: form.releasePolicy === 'AUTO' ? form.releaseAfterDays : undefined,
    }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['organization'] }),
  })

  const resubmit = useMutation({
    mutationFn: () => api.post<OrganizationView>('/api/organizations/me/resubmit'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['organization'] }),
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

  const patchForm = (patch: Partial<typeof form>) => {
    setForm((current) => ({ ...current, ...patch }))
    clearSaveResult()
  }

  const update = (key: keyof typeof form) => (
    event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>,
  ) => {
    setForm((current) => ({ ...current, [key]: event.target.value }))
    clearSaveResult()
  }

  return (
    <div className="max-w-2xl space-y-8">
      <form
        className="space-y-5"
        onSubmit={(event) => { event.preventDefault(); save.mutate() }}
      >
        <h2 className="font-semibold text-slate-800">機構資料</h2>

        <Field label="機構名稱" required>
          <TextInput required maxLength={120} value={form.name} onChange={update('name')} />
        </Field>
        <Field label="承辦人姓名" required hint="平台審核與捐贈者聯繫時的窗口">
          <TextInput required maxLength={100}
            value={form.contactPerson} onChange={update('contactPerson')} />
        </Field>

        {/* 兩個聯絡方式併排；地址獨佔一排——它比另外兩者長得多，擠在半排會被截斷 */}
        <div className="grid gap-5 sm:grid-cols-2">
          <Field label="聯絡信箱" required>
            <TextInput required type="email" maxLength={255}
              value={form.contactEmail} onChange={update('contactEmail')} />
          </Field>
          <Field label="聯絡電話" required>
            <TextInput required maxLength={40}
              value={form.contactPhone} onChange={update('contactPhone')} />
          </Field>
        </div>

        {/* 必填：捐贈者的認領詳情頁靠它顯示禮物要寄去哪裡 */}
        <Field label="收件地址" required hint="捐贈者會把禮物寄到這裡，請填寫完整地址">
          <TextInput required maxLength={255}
            value={form.address} onChange={update('address')} />
        </Field>
        <Field label="方便收件時間" required
          hint="讓捐贈者知道什麼時候送比較方便，例如「平日上午9:00-下午5:00」">
          <TextInput required maxLength={255} placeholder="平日上午9:00-下午5:00"
            value={form.preferredDeliveryTime} onChange={update('preferredDeliveryTime')} />
        </Field>
        <Field label="機構簡介">
          <TextArea rows={4} maxLength={2000}
            value={form.description} onChange={update('description')} />
        </Field>

        <DocumentsField organizationId={organization.id} />

        <hr className="border-santa-100" />

        <h2 className="font-semibold text-slate-800">逾期未寄送的處理</h2>
        <p className="text-sm text-slate-600">
          認領後遲遲未寄送會讓孩子的願望一直卡著。你可以選擇讓系統自動收回，
          或只收到提醒後自行決定。設定變更只影響之後的新認領，不會動到既有的認領。
        </p>

        <div className="space-y-3">
          <PolicyOption
            value="MANUAL"
            current={form.releasePolicy}
            title="我自己決定"
            description="系統只在「逾期提醒」頁標記，由你聯繫捐贈者後決定是否收回。"
            onSelect={(policy) => patchForm({ releasePolicy: policy })}
          />
          <PolicyOption
            value="AUTO"
            current={form.releasePolicy}
            title="自動收回"
            description="超過寬限天數仍未回報寄送，系統自動收回並讓願望重新上架。"
            onSelect={(policy) => patchForm({ releasePolicy: policy })}
          />
        </div>

        {form.releasePolicy === 'AUTO' && (
          <Field label="寬限天數" hint="認領後多少天未寄送就收回（1-60 天）">
            <TextInput type="number" min={1} max={60} required
              value={form.releaseAfterDays}
              onChange={(event) =>
                patchForm({ releaseAfterDays: Number(event.target.value) })} />
          </Field>
        )}

        {save.isError && <ErrorBanner error={save.error} />}
        {save.isSuccess && <Notice tone="success">已儲存。</Notice>}

        <Button type="submit" disabled={save.isPending}>
          {save.isPending ? '儲存中…' : '儲存設定'}
        </Button>
      </form>

      {organization.status === 'REJECTED' && (
        <div className="rounded-xl bg-white p-5 ring-1 ring-santa-100">
          <h2 className="font-semibold text-slate-800">重新送審</h2>
          <p className="mt-1 text-sm text-slate-600">
            資料補齊後可以再次送審，平台會重新檢視。
          </p>
          {resubmit.isError && <div className="mt-3"><ErrorBanner error={resubmit.error} /></div>}
          <Button className="mt-4" disabled={resubmit.isPending} onClick={() => resubmit.mutate()}>
            {resubmit.isPending ? '送出中…' : '重新送審'}
          </Button>
        </div>
      )}
    </div>
  )
}

/**
 * 申請時上傳的立案證明文件。跟其他欄位一樣用 Field 呈現，不要另起一張卡片
 * ——版面佈局照抄 OrgApply.tsx 同名欄位的寫法，只是那邊列的是「選取但未
 * 上傳的檔案」，這邊列的是「已上傳的文件下載連結」。
 *
 * 不受機構審核狀態限制顯示——PENDING／APPROVED／REJECTED／SUSPENDED 都看得到，
 * 跟後端 authorizeOrgDocument 不檢查機構狀態一致。沒有刪除按鈕：ORG_DOCUMENT
 * 不支援自行刪除（AttachmentService.requireDeletablePurpose 無條件擋下，
 * 管理員也不能透過一般附件端點刪），需要更換只能聯繫平台管理員。
 */
function DocumentsField({ organizationId }: { organizationId: string }) {
  const documents = useQuery({
    queryKey: ['organization', 'me', 'documents'],
    queryFn: () => api.get<AttachmentView[]>('/api/organizations/me/documents'),
  })
  const count = documents.data?.length ?? 0
  const atLimit = count >= MAX_ORG_DOCUMENTS

  return (
    <Field label="相關文件證明"
      hint={`已上傳 ${count} / ${MAX_ORG_DOCUMENTS} 份；需要更換或刪除請聯繫平台管理員協助處理`}>
      <div className="space-y-2">
        {documents.isLoading && <Spinner label="載入文件" />}
        {documents.isError && <ErrorBanner error={documents.error} />}
        {documents.isFetched && !documents.isLoading && count > 0 && (
          <ul className="space-y-1.5">
            {documents.data?.map((doc) => (
              <li key={doc.id}>
                <a href={doc.url} target="_blank" rel="noreferrer"
                  className="inline-flex items-center gap-1.5 text-sm text-santa-700 hover:underline">
                  {doc.contentType === 'application/pdf' ? '📄' : '🖼️'} {doc.fileName ?? '下載文件'}
                </a>
              </li>
            ))}
          </ul>
        )}
        {!atLimit && (
          <ImageUploader purpose="ORG_DOCUMENT" targetId={organizationId} label="新增文件"
            accept={ACCEPTED_ORG_DOCUMENT_TYPES} validate={validateOrgDocument}
            onUploaded={() => void documents.refetch()} />
        )}
      </div>
    </Field>
  )
}

function PolicyOption({ value, current, title, description, onSelect }: {
  value: ReleasePolicy
  current: ReleasePolicy
  title: string
  description: string
  onSelect: (policy: ReleasePolicy) => void
}) {
  const selected = current === value
  return (
    <label className={`flex cursor-pointer gap-3 rounded-lg border p-4 transition-colors ${
      selected ? 'border-santa-500 bg-santa-50' : 'border-slate-200 bg-white hover:bg-slate-50'
    }`}>
      <input
        type="radio"
        name="releasePolicy"
        className="mt-1"
        checked={selected}
        onChange={() => onSelect(value)}
      />
      <span>
        <span className="block text-sm font-medium text-slate-800">{title}</span>
        <span className="mt-0.5 block text-sm text-slate-600">{description}</span>
      </span>
    </label>
  )
}
