import { AttachmentGallery } from '../../components/console/ImageViewer'
import { formatDateTime } from '../../lib/format'
import type { AttachmentView } from '../../lib/types'

/** 一組證明的小標題：誰上傳、何時、幾張。 */
function Group({ title, files, by, empty, altPrefix }: {
  title: string
  files: AttachmentView[]
  by: string
  empty: string
  altPrefix: string
}) {
  const first = files.map((file) => file.uploadedAt).sort()[0]
  return (
    <div className="att-group">
      <p className="att-group__head">
        <strong>{title}</strong>
        {files.length > 0 && <span>{by} 上傳・{formatDateTime(first)}・{files.length} 張</span>}
      </p>
      {files.length === 0
        ? <p className="auth-note">{empty}</p>
        : <AttachmentGallery files={files} altPrefix={altPrefix} />}
    </div>
  )
}

/**
 * 管理員看的認領證明：把認領者回報寄送時上傳的「寄件證明」與機構確認收到時上傳的「收件證明」
 * 分成兩組標示，才分得出哪張是誰上傳的。
 */
export function ClaimProofs({ files, donorName, organizationName, received }: {
  files: AttachmentView[]
  donorName: string
  organizationName: string
  /** 機構是否已確認收到 */
  received: boolean
}) {
  return (
    <>
      <Group title="認領者寄件證明" altPrefix="寄件證明" by={donorName} empty="沒有寄件證明。"
        files={files.filter((file) => file.purpose === 'SHIPPING_PROOF')} />
      <Group title="機構收件證明" altPrefix="收件證明" by={organizationName}
        empty={received ? '機構確認收到時沒有附照片。' : '機構還沒確認收到。'}
        files={files.filter((file) => file.purpose === 'ORG_FEEDBACK')} />
    </>
  )
}
