/**
 * 願望示意圖是否開放上傳。
 *
 * 關閉時願望牆改以分類圖示呈現，公開 bucket 因此不必存在，也就沒有它的出網流量。
 * 後端有對應的 `app.storage.wish-image-enabled`——那一道才是真正的界線，
 * 這裡只是不要把按鈕畫出來。兩邊要一起開啟，見 docs/DEPLOY.md。
 */
export const WISH_IMAGE_ENABLED = import.meta.env.VITE_WISH_IMAGE_ENABLED === 'true'

/**
 * 分類對應的圖示，用在沒有示意圖的時候。
 *
 * 不是每一格都放同一個 🎁——一頁 20 格一模一樣的禮物盒，願望牆就失去了辨識度，
 * 捐贈者也少了往下捲的理由。emoji 不佔 bundle 也不產生任何流量。
 */
const CATEGORY_ICONS: Record<string, string> = {
  TOY: '🧸',
  BOOK: '📚',
  CLOTHING: '👕',
  SPORTS: '⚽',
  STATIONERY: '✏️',
  ELECTRONICS: '🎧',
  MUSIC: '🎸',
  ART: '🎨',
  DAILY_NECESSITIES: '🧴',
  OTHER: '🎁',
}

/** 後端新增分類但前端還沒補圖示時，退回通用的禮物盒而不是留白。 */
export function wishIcon(category: string): string {
  return CATEGORY_ICONS[category] ?? '🎁'
}
