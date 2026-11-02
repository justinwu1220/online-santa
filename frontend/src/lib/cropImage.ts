import { markReadyToUpload } from './upload'

export interface CropArea {
  x: number
  y: number
  width: number
  height: number
}

const OUTPUT_QUALITY = 0.85

/** 依比例算出輸出尺寸（寬固定，高四捨五入）。 */
export function outputSize(aspect: number, width: number) {
  return { width, height: Math.round(width / aspect) }
}

/**
 * 把使用者選的範圍裁成固定尺寸的 JPEG。原圖只在瀏覽器裡用，上傳的只有這份結果。
 *
 * 用 createImageBitmap 的 `imageOrientation: 'from-image'` 吃掉 EXIF 方向，
 * 與裁切元件顯示的（瀏覽器已轉正的）圖片座標一致。
 */
export async function cropImage(file: File, area: CropArea, width: number, aspect: number): Promise<File> {
  const size = outputSize(aspect, width)
  const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' })
  try {
    const canvas = document.createElement('canvas')
    canvas.width = size.width
    canvas.height = size.height
    const ctx = canvas.getContext('2d')
    if (!ctx) throw new Error('無法取得 canvas context')
    ctx.drawImage(bitmap, area.x, area.y, area.width, area.height, 0, 0, size.width, size.height)
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', OUTPUT_QUALITY))
    if (!blob) throw new Error('圖片編碼失敗')
    const name = file.name.replace(/\.[^.]+$/, '') + '.jpg'
    return markReadyToUpload(new File([blob], name, { type: 'image/jpeg' }))
  } finally {
    bitmap.close()
  }
}
