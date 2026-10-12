import { useRef, useState } from 'react'
import { ACCEPTED_IMAGE_TYPES, uploadImage, validateImage } from '../lib/upload'
import type { AttachmentPurpose, AttachmentView } from '../lib/types'
import { Button } from './Form'
import { ErrorBanner } from './Feedback'

/**
 * 封裝三步驟上傳流程的按鈕。
 *
 * 上傳中會停用，避免使用者連按產生多個孤兒附件。
 */
export function ImageUploader({ purpose, targetId, label, onUploaded, disabled,
  accept = ACCEPTED_IMAGE_TYPES, validate = validateImage }: {
  purpose: AttachmentPurpose
  targetId: string
  label: string
  /**
   * 帶回剛上傳成功的附件，讓呼叫端可以直接更新自己的本機清單——只呼叫一個
   * 沒有參數的 callback（例如重新整理外層的列表查詢）常常不夠：外層那個
   * query 重新抓資料，不代表呼叫端手上這個元件已經拿到的物件（例如正在
   * 編輯中的某筆資料）會跟著變新，畫面會卡住看不到剛上傳的結果。
   */
  onUploaded: (attachment: AttachmentView) => void
  disabled?: boolean
  /** 預設只收圖片；機構文件等非純圖片用途可傳入 ACCEPTED_ORG_DOCUMENT_TYPES */
  accept?: readonly string[]
  validate?: (file: File) => string | null
}) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<unknown>(null)

  async function handleFile(file: File) {
    const problem = validate(file)
    if (problem) {
      setError(new Error(problem))
      return
    }

    setError(null)
    setUploading(true)
    try {
      const attachment = await uploadImage(purpose, targetId, file)
      onUploaded(attachment)
    } catch (uploadError) {
      setError(uploadError)
    } finally {
      setUploading(false)
      // 清空才能重新選同一個檔案
      if (inputRef.current) inputRef.current.value = ''
    }
  }

  return (
    <div className="space-y-2">
      <input
        ref={inputRef}
        type="file"
        className="hidden"
        accept={accept.join(',')}
        onChange={(event) => {
          const file = event.target.files?.[0]
          if (file) void handleFile(file)
        }}
      />
      <Button
        variant="secondary"
        disabled={disabled || uploading}
        onClick={() => inputRef.current?.click()}
      >
        {uploading ? '上傳中…' : label}
      </Button>
      {error != null && <ErrorBanner error={error} />}
    </div>
  )
}
