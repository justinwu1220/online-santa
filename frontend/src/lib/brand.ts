/**
 * 平台名稱。
 *
 * 集中在一處，是因為它散落在五個入口的標頭與每一頁的 document.title 裡。哪天要改名
 * （或加上贊助單位），不必去翻十幾個檔案還漏掉一兩處。
 */
export const PLATFORM_NAME = '線上聖誕老人'

/** 這一屆的年份：首頁大標與搜尋結果大標題都帶著它。換年份時只改這裡（還有 index.html）。 */
export const CAMPAIGN_YEAR = '2026'

/**
 * 首頁（也就是搜尋結果大標題）的活動全名。
 *
 * 《Google 搜尋上線與排名改善指南》（2026-11-13）的定稿文字，與 index.html 的 `<title>`
 * 一字不差——首頁是 WishWall，它不自己設標題，但其他頁面離開時會用 `pageTitle()` 還原，
 * 兩邊不一致的話，從內頁回到首頁分頁標題就會縮水成平台名稱。改文案兩邊要一起改。
 */
export const SITE_TITLE = `${PLATFORM_NAME} × 社團法人彰化縣康乃心關懷協會 ${CAMPAIGN_YEAR}`

/** 標頭用的品牌字串，帶聖誕樹。 */
export const BRAND = `🎄 ${PLATFORM_NAME}`

/**
 * 這個網站是哪個環境（例如「測試站」）。正式站不設定，回傳空字串。
 *
 * 建置時由 `VITE_ENV_LABEL` 帶入。用函式而不是常數，是為了讓測試可以用 `vi.stubEnv` 改它。
 */
export const envLabel = () => (import.meta.env.VITE_ENV_LABEL ?? '').trim()

/**
 * 瀏覽器分頁標題。`pageTitle()` 不帶參數時是首頁的活動全名（{@link SITE_TITLE}）。
 *
 * 非正式環境會在最前面加上 `[測試站]`：全站每一頁的分頁標題都經過這裡，同時開著測試站與正式站時，
 * 光看分頁就分得出來。
 */
export const pageTitle = (page?: string) => {
  const title = page ? `${page} — ${PLATFORM_NAME}` : SITE_TITLE
  const label = envLabel()
  return label ? `[${label}] ${title}` : title
}
