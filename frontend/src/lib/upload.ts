import { api } from './api'
import type { AttachmentPurpose, AttachmentView, UploadUrlResponse } from './types'

/**
 * 圖片上傳的三步驟。
 *
 * 檔案不經過我們的 API：後端只負責發網址與事後查證，位元組直接進儲存端。
 * 這省下 Cloud Run 的運算時間，也不受請求逾時限制。
 */
export async function uploadImage(
  purpose: AttachmentPurpose,
  targetId: string,
  file: File,
): Promise<AttachmentView> {
  // 一、向後端索取限時的直傳網址
  const target = await api.post<UploadUrlResponse>('/api/uploads/signed-url', {
    purpose,
    targetId,
    contentType: file.type,
    sizeBytes: file.size,
  })

  // 二、直接把檔案 PUT 到儲存端。Content-Type 必須與簽章時一致，否則會被拒絕
  const response = await fetch(target.uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': target.contentType },
    body: file,
  })
  if (!response.ok) {
    throw new Error(`檔案上傳失敗（${response.status}），請稍後再試`)
  }

  // 三、回頭確認。後端會向儲存端查證檔案確實存在、型別與大小也符合
  return api.post<AttachmentView>(`/api/attachments/${target.attachmentId}/confirm`)
}

export const ACCEPTED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']
export const MAX_IMAGE_BYTES = 5 * 1024 * 1024

/** 在送出前先擋掉明顯不合規的檔案，省去一次往返。 */
export function validateImage(file: File): string | null {
  if (!ACCEPTED_IMAGE_TYPES.includes(file.type)) {
    return '只接受 JPEG、PNG 或 WebP 圖片'
  }
  if (file.size > MAX_IMAGE_BYTES) {
    return '圖片不可超過 5 MB'
  }
  return null
}
