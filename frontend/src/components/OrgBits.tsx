import { Building2, ExternalLink } from 'lucide-react'

/**
 * 機構照片與官方網站按鈕。後端還沒有這兩個欄位（見 docs/redesign-1018-deferred.md），
 * 先把版面做出來：沒有照片時顯示虛線佔位，沒有連結時按鈕停用並以提示說明。
 */
export function OrgPhoto({ src, mini = false }: { src?: string | null; mini?: boolean }) {
  const cls = mini ? 'mini-org__photo' : 'org__photo'
  if (src) return <div className={cls}><img src={src} alt="" /></div>
  return (
    <div className={`${cls} ${cls}--empty`} role="img" aria-label="此機構尚未提供照片">
      <Building2 className="icon" size={mini ? 28 : 36} aria-hidden />
      <span>尚未提供機構照片</span>
    </div>
  )
}

export function OrgSiteButton({ href }: { href?: string | null }) {
  if (href) {
    return (
      <a className="btn org__link" href={href} target="_blank" rel="noopener noreferrer">
        <ExternalLink className="icon" aria-hidden />機構官方網站
      </a>
    )
  }
  // title 放在外層：停用的按鈕本身在部分瀏覽器不會顯示提示
  return (
    <span className="org__link-wrap" title="此機構未提供連結">
      <button type="button" className="btn org__link" disabled aria-disabled="true">
        <ExternalLink className="icon" aria-hidden />機構官方網站
      </button>
    </span>
  )
}
