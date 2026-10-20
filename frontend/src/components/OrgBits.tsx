import { ExternalLink } from 'lucide-react'
import { useState } from 'react'
import { ImageLightbox } from './console/ImageViewer'

/**
 * 機構照片與官方網站連結，結構照 prototype.html。機構沒上傳照片或沒填官網時
 * 顯示佔位與停用狀態（舊機構在補填之前都是這樣）。
 */
export function OrgPhoto({ src, orgName, mini = false }: {
  src?: string | null
  orgName?: string
  mini?: boolean
}) {
  const cls = mini ? 'mini-org__photo' : `org__photo${src ? '' : ' org__photo--empty'}`
  const alt = `${orgName ?? '機構'}的照片`
  const [open, setOpen] = useState(false)
  return (
    <div className={cls}>
      {src ? (
        <>
          <button type="button" className="org__photo-open" onClick={() => setOpen(true)}
            aria-label={`放大檢視：${alt}`}>
            <img src={src} alt={alt} />
          </button>
          <ImageLightbox images={[{ id: src, url: src, alt }]} index={open ? 0 : null}
            onIndex={() => {}} onClose={() => setOpen(false)} />
        </>
      ) : '【機構照片】'}
    </div>
  )
}

export function OrgSiteLink({ href }: { href?: string | null }) {
  const text = '前往機構官方網站'
  if (href) {
    return (
      <a className="org__link" href={href} target="_blank" rel="noopener">
        {text}<ExternalLink className="icon" aria-hidden />
      </a>
    )
  }
  // 沒有 href 的 <a> 不能點、也不會被 Tab 聚焦；title 直接掛在同一個元素上
  return (
    <a className="org__link" aria-disabled="true" title="此機構未提供連結">
      {text}<ExternalLink className="icon" aria-hidden />
    </a>
  )
}
