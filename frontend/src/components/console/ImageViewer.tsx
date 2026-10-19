import { ChevronLeft, ChevronRight, X, ZoomIn } from 'lucide-react'
import { useEffect, useState } from 'react'
import { isImage } from '../../lib/fileType'
import { Dialog } from '../ui/Dialog'

/** 後台圖片的縮圖與放大檢視。縮圖用 `object-fit: cover`，點擊後在彈窗裡完整顯示（`object-fit: contain`）。 */
export interface ViewerImage {
  id: string
  url: string
  alt: string
}

/**
 * 放大檢視彈窗。同一組有多張時顯示左右箭頭與「n / N」，鍵盤左右鍵也能切換；
 * Esc、點背景或右上角的關閉鈕關閉。
 */
export function ImageLightbox({ images, index, onIndex, onClose }: {
  images: ViewerImage[]
  /** 目前顯示第幾張；null 代表關閉 */
  index: number | null
  onIndex: (next: number) => void
  onClose: () => void
}) {
  const open = index !== null
  const total = images.length
  // 關閉動畫期間還要顯示最後那張圖，所以記住最後一次的索引
  const [last, setLast] = useState(0)
  if (index !== null && index !== last) setLast(index)
  const current = images[Math.min(last, total - 1)]
  const [failed, setFailed] = useState<string | null>(null)

  useEffect(() => {
    if (!open || total < 2) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'ArrowRight') { event.preventDefault(); onIndex((last + 1) % total) }
      if (event.key === 'ArrowLeft') { event.preventDefault(); onIndex((last - 1 + total) % total) }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, total, last, onIndex])

  if (!current) return null
  return (
    <Dialog open={open} onClose={onClose} className="letter-dialog lightbox" labelledBy="lightboxTitle">
      <div className="letter-dialog__body">
        <span id="lightboxTitle" className="sr-only">放大檢視：{current.alt}</span>
        <button type="button" className="letter-dialog__close" onClick={onClose} aria-label="關閉">
          <X className="icon" aria-hidden />
        </button>
        {failed === current.url
          ? <p className="lightbox__fail">無法載入圖片</p>
          : <img src={current.url} alt={current.alt} onError={() => setFailed(current.url)} />}
        {total > 1 && (
          <>
            <button type="button" className="lightbox__nav lightbox__nav--prev" aria-label="上一張"
              onClick={() => onIndex((last - 1 + total) % total)}>
              <ChevronLeft className="icon" aria-hidden />
            </button>
            <button type="button" className="lightbox__nav lightbox__nav--next" aria-label="下一張"
              onClick={() => onIndex((last + 1) % total)}>
              <ChevronRight className="icon" aria-hidden />
            </button>
            <span className="lightbox__count" aria-live="polite">{last + 1} / {total}</span>
          </>
        )}
      </div>
    </Dialog>
  )
}

/** 縮圖按鈕：點擊呼叫 `onOpen`。圖片載入失敗顯示佔位字，不留破圖。 */
export function Thumb({ src, alt, onOpen, className = '' }: {
  src: string
  alt: string
  onOpen: () => void
  className?: string
}) {
  const [failed, setFailed] = useState(false)
  return (
    <button type="button" className={`thumb ${className}`} onClick={onOpen} aria-label={`放大檢視：${alt}`}>
      {failed
        ? <span className="thumb__fail">無法載入</span>
        : <img src={src} alt="" onError={() => setFailed(true)} />}
      <span className="thumb__zoom" aria-hidden><ZoomIn className="icon" /></span>
    </button>
  )
}

/** 一組縮圖共用一個放大彈窗（點任何一張都能在彈窗裡切換同組的其他張）。 */
export function ImageGallery({ images, className = 'att-grid' }: { images: ViewerImage[]; className?: string }) {
  const [index, setIndex] = useState<number | null>(null)
  if (images.length === 0) return null
  return (
    <>
      <div className={className}>
        {images.map((image, i) => (
          <Thumb key={image.id} src={image.url} alt={image.alt} onOpen={() => setIndex(i)} />
        ))}
      </div>
      <ImageLightbox images={images} index={index} onIndex={setIndex} onClose={() => setIndex(null)} />
    </>
  )
}

/** 單張預覽（例如剛選好、尚未上傳的本機圖）：縮圖框內顯示圖片，點擊放大。沒有圖片時只顯示佔位內容。 */
export function PreviewImage({ src, alt, children }: { src?: string | null; alt: string; children?: React.ReactNode }) {
  const [open, setOpen] = useState(false)
  if (!src) return <>{children}</>
  return (
    <>
      <button type="button" className="preview-zoom" onClick={() => setOpen(true)} aria-label={`放大檢視：${alt}`}>
        <img src={src} alt="" />
        <span className="thumb__zoom" aria-hidden><ZoomIn className="icon" /></span>
      </button>
      <ImageLightbox images={[{ id: src, url: src, alt }]} index={open ? 0 : null}
        onIndex={() => {}} onClose={() => setOpen(false)} />
    </>
  )
}

/**
 * 一組附件：圖片顯示縮圖（點擊放大，同組可切換），PDF 等其他檔案維持檔名連結（開新分頁）。
 * 後台的附件清單（信件、寄件照片、收件證明、機構文件、發票）都用這個。
 */
export function AttachmentGallery({ files, altPrefix }: {
  files: { id: string; url: string; contentType: string; fileName?: string }[]
  altPrefix: string
}) {
  const images = files.filter((file) => isImage(file.contentType))
    .map((file, i) => ({ id: file.id, url: file.url, alt: file.fileName ?? `${altPrefix} ${i + 1}` }))
  const others = files.filter((file) => !isImage(file.contentType))
  return (
    <>
      <ImageGallery images={images} />
      {others.length > 0 && (
        <div className="att-grid">
          {others.map((file) => (
            <a key={file.id} className="att att--photo" href={file.url} target="_blank" rel="noopener noreferrer">
              {file.fileName ?? '檔案'}
            </a>
          ))}
        </div>
      )}
    </>
  )
}
