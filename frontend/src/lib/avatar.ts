/**
 * 兒童頭貼分配（SPEC 14.1）。
 *
 * 與孩子的名字、性別無關：只有 0–3 歲用 baby1–4，其他年齡從 boy／girl 10 張挑一張。
 *
 * 設計稿要求「建立願望時挑一次、存進資料庫」。後端目前沒有 avatar 欄位（見
 * docs/redesign-1018-deferred.md #8），所以這裡用願望 id 做穩定雜湊：同一個願望永遠是同一張，
 * 列表、詳細頁、我的認領都一致，重新整理也不會變。之後補了欄位，這個函式直接換成讀存庫的值。
 */
const BABY = ['baby1.jpg', 'baby2.jpg', 'baby3.png', 'baby4.png']
const KID = [
  'boy1.jpg', 'boy2.png', 'boy3.png', 'boy4.png', 'boy5.png',
  'girl1.png', 'girl2.jpg', 'girl3.png', 'girl4.jpg', 'girl5.jpg',
]

/** FNV-1a 32 位元雜湊。不需要密碼學強度，只要穩定、分布平均。 */
export function stableHash(text: string): number {
  let hash = 0x811c9dc5
  for (let i = 0; i < text.length; i += 1) {
    hash ^= text.charCodeAt(i)
    hash = Math.imul(hash, 0x01000193)
  }
  return hash >>> 0
}

/** @param ageRange 後端的 AgeRange 值，例如 `AGE_0_3` */
export function avatarFor(wishId: string, ageRange: string): string {
  const pool = ageRange === 'AGE_0_3' ? BABY : KID
  return `/avatars/${pool[stableHash(wishId) % pool.length]}`
}
