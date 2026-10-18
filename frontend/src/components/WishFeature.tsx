import { Gift, Mail, X, ZoomIn } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { avatarSrc } from '../lib/avatar'
import type { PublicStatus } from '../lib/wishStatus'
import type { WishPublicView } from '../lib/types'
import { Dialog } from './ui/Dialog'

/**
 * 願望大卡片（SPEC 4）：左邊是孩子的手寫信件（主角），右邊是資訊欄與行動區。
 *
 * 願望詳細頁與「我的認領」頁共用同一張卡片，只有右上的狀態標籤與資訊欄最下方的
 * `footer` 不同：詳細頁放認領按鈕，認領後放「你的進度」。狀態只由 `data-status` 推導樣式。
 */
export function WishFeature({ wish, statusKey, statusLabel, footer, orgAnchor }: {
  wish: WishPublicView
  statusKey: PublicStatus
  statusLabel: string
  footer: ReactNode
  /** 機構名稱要不要做成「捲到機構區」的連結（頁內錨點，只捲動、不換頁） */
  orgAnchor?: string
}) {
  const [zoomed, setZoomed] = useState(false)
  const [photoIndex, setPhotoIndex] = useState(0)

  const photos = wish.letterPhotoUrls
  const photo = photos[Math.min(photoIndex, photos.length - 1)]

  return (
    <article className="wish" data-status={statusKey}>
      <span className="status-tab status-tab--lg">{statusLabel}</span>

      <section className="letter" aria-label={`${wish.childAlias}想對你說`}>
        <div className="letter__head">
          <Mail className="icon" aria-hidden />
          <h1 className="letter__title">{wish.childAlias}想對你說</h1>
        </div>
        <figure className="letter__figure">
          {photo ? (
            <button type="button" className="letter__frame" onClick={() => setZoomed(true)}
              aria-label="放大閱讀信件">
              <img src={photo} alt={`${wish.childAlias}的手寫信件`} />
              <span className="letter__zoom"><ZoomIn className="icon" aria-hidden />放大閱讀</span>
            </button>
          ) : (
            <div className="letter__frame" style={{ cursor: 'default' }}>
              <div className="letter__placeholder"><span>手寫信件即將上傳</span></div>
            </div>
          )}
          {photos.length > 1 && (
            <div className="letter__thumbs">
              {photos.map((url, index) => (
                <button key={url} type="button" className="letter__thumb"
                  aria-current={index === photoIndex} aria-label={`第 ${index + 1} 張`}
                  onClick={() => setPhotoIndex(index)}>
                  <img src={url} alt="" />
                </button>
              ))}
            </div>
          )}
        </figure>
      </section>

      <aside className="info">
        <div className="who">
          <img className="avatar" src={avatarSrc(wish.avatar)} alt="" width={80} height={80} />
          <div className="who__text">
            <div className="who__name-row">
              <span className="who__name">{wish.childAlias}</span>
              <span className="who__age">{wish.ageRangeLabel}</span>
            </div>
            <div className="who__org">
              {orgAnchor ? (
                <a href={`#${orgAnchor}`} onClick={(event) => {
                  event.preventDefault()
                  document.getElementById(orgAnchor)?.scrollIntoView({ behavior: 'smooth' })
                }}>{wish.organizationName}</a>
              ) : wish.organizationName}
            </div>
          </div>
        </div>

        <div className="gift-block">
          <p className="gift gift--lg">
            <Gift className="icon" aria-hidden />
            <span>{wish.title}</span>
          </p>
          {(wish.note || wish.description) && (
            <p className="gift-note" style={{ whiteSpace: 'pre-wrap' }}>{wish.note || wish.description}</p>
          )}
          {wish.interests && <p className="gift-note">孩子喜歡：{wish.interests}</p>}
        </div>

        <div className="info__spacer" />

        {footer}
      </aside>

      <Dialog open={zoomed} onClose={() => setZoomed(false)} className="letter-dialog">
        <div className="letter-dialog__body">
          <button type="button" className="letter-dialog__close" onClick={() => setZoomed(false)}
            aria-label="關閉">
            <X className="icon" aria-hidden />
          </button>
          {photo && <img src={photo} alt={`${wish.childAlias}的手寫信件`} />}
        </div>
      </Dialog>
    </article>
  )
}
