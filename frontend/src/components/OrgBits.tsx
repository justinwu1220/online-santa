import { ExternalLink } from 'lucide-react'

/**
 * 機構照片與官方網站連結，結構照 prototype.html。後端還沒有這兩個欄位
 * （見 docs/redesign-1018-deferred.md #12），所以目前一律是佔位與停用狀態；
 * 之後有資料時傳入 `src`／`href` 即可。
 */
export function OrgPhoto({ src, orgName, mini = false }: {
  src?: string | null
  orgName?: string
  mini?: boolean
}) {
  const cls = mini ? 'mini-org__photo' : `org__photo${src ? '' : ' org__photo--empty'}`
  return (
    <div className={cls}>
      {src ? <img src={src} alt={`${orgName ?? '機構'}的照片`} /> : '【機構照片】'}
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
