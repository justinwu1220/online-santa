import { formatDateTime } from '../../lib/format'
import type { WishChanges, WishHistoryItem } from '../../lib/types'

/**
 * 修改後重新送審：和上次核准的版本相比，改了什麼。文字欄位列出「舊 → 新」，
 * 信件照片說明張數與有沒有更動（新的照片在上方「孩子的願望」直接看得到）。
 * 沒有上次核准的紀錄（功能上線前就核准的舊願望）時只說明無法比較。
 */
export function ResubmitChanges({ changes }: { changes?: WishChanges }) {
  if (!changes) {
    return <p className="auth-note">沒有上次核准版本的紀錄（這個願望是在這項功能上線之前核准的），無法比較改了什麼，請直接檢查現在的內容。</p>
  }
  const nothing = changes.fields.length === 0 && !changes.lettersChanged
  return (
    <div className="wh-changes">
      <p className="wh-changes__since">與 {formatDateTime(changes.lastApprovedAt)} 核准的版本相比：</p>
      {nothing && <p className="auth-note">文字與信件照片都沒有變動。</p>}
      {changes.fields.map((change) => (
        <div key={change.field} className="wh-change">
          <strong>{change.field}</strong>
          <span className="wh-change__old">{change.before || '（空白）'}</span>
          <span className="wh-change__arrow" aria-hidden>→</span>
          <span className="wh-change__new">{change.after || '（空白）'}</span>
        </div>
      ))}
      {changes.lettersChanged && (
        <div className="wh-change">
          <strong>孩子的手寫信件照片</strong>
          <span>已更換（{changes.lettersBefore} 張 → {changes.lettersAfter} 張），請看上方「孩子的願望」裡的新照片</span>
        </div>
      )}
    </div>
  )
}

/** 願望歷程時間軸：由舊到新，每筆附時間與原因（退回、凍結的原因）。 */
export function WishHistoryTimeline({ items }: { items: WishHistoryItem[] }) {
  if (items.length === 0) {
    return <p className="auth-note">沒有更早的紀錄（這個願望是在紀錄功能上線之前建立的）。</p>
  }
  return (
    <ol className="wh-timeline">
      {items.map((item, index) => (
        <li key={`${item.type}-${item.at}-${index}`} data-type={item.type}>
          <span className="wh-timeline__time">{formatDateTime(item.at)}</span>
          <strong>{item.label}</strong>
          {item.note && <span className="wh-timeline__note">{item.note}</span>}
        </li>
      ))}
    </ol>
  )
}
