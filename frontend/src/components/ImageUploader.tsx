import { useRef, useState } from 'react'
import { ACCEPTED_IMAGE_TYPES, uploadImage, validateImage } from '../lib/upload'
import type { AttachmentPurpose } from '../lib/types'
import { Button } from './Form'
import { ErrorBanner } from './Feedback'

/**
 * 封裝三步驟上傳流程的按鈕。
 *
 * 上傳中會停用，避免使用者連按產生多個孤兒附件。
 */
export function ImageUploader({ purpose, targetId, label, onUploaded, disabled }: {
  purpose: AttachmentPurpose
  targetId: string
  label: string
  onUploaded: () => void
  disabled?: boolean
}) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<unknown>(null)

  async function handleFile(file: File) {
    const problem = validateImage(file)
    if (problem) {
      setError(new Error(problem))
      return
    }

    setError(null)
    setUploading(true)
    try {
      await uploadImage(purpose, targetId, file)
      onUploaded()
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
        accept={ACCEPTED_IMAGE_TYPES.join(',')}
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
