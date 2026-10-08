import { api } from './api'
import type { AttachmentPurpose, AttachmentView, UploadUrlResponse } from './types'

const COMPRESS_MAX_DIMENSION = 1280
const COMPRESS_QUALITY = 0.8
// 已經夠小就別再重編碼一次，省一趟 canvas 往返
const SKIP_COMPRESSION_MAX_BYTES = 300 * 1024

/**
 * 上傳前在瀏覽器端把圖片壓小：長邊縮到 1280px、輸出 JPEG 品質 0.8。
 *
 * 輸出固定用 JPEG，不用 WebP——canvas.toBlob 對 WebP 的支援在舊版 Safari 不穩定，
 * 而 JPEG 0.8 品質已經足夠壓低 egress，不值得為此多一層瀏覽器能力偵測。
 * 用 createImageBitmap 而非 <img> 是因為 { imageOrientation: 'from-image' } 能
 * 正確吃掉 EXIF 方向，避免直向照片壓完變橫的。
 *
 * 任何一步失敗（罕見格式、瀏覽器不支援）都退回原始檔案，不擋使用者上傳。
 */
async function compressImage(file: File): Promise<File> {
  // PDF（或其他非圖片格式）沒辦法丟進 canvas 解壓縮，原樣直接送出
  if (!ACCEPTED_IMAGE_TYPES.includes(file.type)) return file

  try {
    const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' })
    try {
      const longEdge = Math.max(bitmap.width, bitmap.height)
      if (file.size <= SKIP_COMPRESSION_MAX_BYTES && longEdge <= COMPRESS_MAX_DIMENSION) {
        return file
      }

      const scale = Math.min(1, COMPRESS_MAX_DIMENSION / longEdge)
      const width = Math.round(bitmap.width * scale)
      const height = Math.round(bitmap.height * scale)

      const canvas = document.createElement('canvas')
      canvas.width = width
      canvas.height = height
      const ctx = canvas.getContext('2d')
      if (!ctx) throw new Error('無法取得 canvas context')
      ctx.drawImage(bitmap, 0, 0, width, height)

      const blob = await new Promise<Blob | null>((resolve) =>
        canvas.toBlob(resolve, 'image/jpeg', COMPRESS_QUALITY))
      if (!blob) throw new Error('圖片編碼失敗')

      const compressedName = file.name.replace(/\.[^.]+$/, '') + '.jpg'
      return new File([blob], compressedName, { type: 'image/jpeg' })
    } finally {
      bitmap.close()
    }
  } catch (error) {
    console.warn('圖片壓縮失敗，改用原始檔案上傳', error)
    return file
  }
}

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
  const upload = await compressImage(file)

  // 一、向後端索取限時的直傳網址
  const target = await api.post<UploadUrlResponse>('/api/uploads/signed-url', {
    purpose,
    targetId,
    contentType: upload.type,
    sizeBytes: upload.size,
  })

  // 二、直接把檔案 PUT 到儲存端。Content-Type 必須與簽章時一致，否則會被拒絕
  const response = await fetch(target.uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': target.contentType },
    body: upload,
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
export function validateFile(
  file: File,
  acceptedTypes: readonly string[],
  maxBytes: number,
  typeLabel: string,
): string | null {
  if (!acceptedTypes.includes(file.type)) {
    return `只接受 ${typeLabel} 格式`
  }
  if (file.size > maxBytes) {
    return `檔案不可超過 ${maxBytes / (1024 * 1024)} MB`
  }
  return null
}

export function validateImage(file: File): string | null {
  return validateFile(file, ACCEPTED_IMAGE_TYPES, MAX_IMAGE_BYTES, 'JPEG、PNG 或 WebP 圖片')
}

// 機構申請文件：圖檔或 PDF 皆可——任意格式會重新打開腳本/巨集偽裝檔案的風險，
// 管理員又是全站權限最高的帳號，被釣魚的代價更高，所以限制在這個白名單內
export const ACCEPTED_ORG_DOCUMENT_TYPES = [...ACCEPTED_IMAGE_TYPES, 'application/pdf'] as const
// 跟後端 app.storage.max-upload-bytes 一致（單一全站上限，不分用途）
export const MAX_ORG_DOCUMENT_BYTES = MAX_IMAGE_BYTES

export function validateOrgDocument(file: File): string | null {
  return validateFile(file, ACCEPTED_ORG_DOCUMENT_TYPES, MAX_ORG_DOCUMENT_BYTES, '圖檔（JPEG/PNG/WebP）或 PDF')
}
