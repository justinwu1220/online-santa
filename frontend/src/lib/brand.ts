/**
 * 平台名稱。
 *
 * 集中在一處，是因為它散落在五個入口的標頭與每一頁的 document.title 裡。哪天要改名
 * （或加上贊助單位），不必去翻十幾個檔案還漏掉一兩處。
 */
export const PLATFORM_NAME = '線上聖誕老人'

/** 標頭用的品牌字串，帶聖誕樹。 */
export const BRAND = `🎄 ${PLATFORM_NAME}`

/**
 * 這個網站是哪個環境（例如「測試站」）。正式站不設定，回傳空字串。
 *
 * 建置時由 `VITE_ENV_LABEL` 帶入。用函式而不是常數，是為了讓測試可以用 `vi.stubEnv` 改它。
 */
export const envLabel = () => (import.meta.env.VITE_ENV_LABEL ?? '').trim()

/**
 * 瀏覽器分頁標題。`pageTitle()` 不帶參數時就是平台名稱本身。
 *
 * 非正式環境會在最前面加上 `[測試站]`：全站每一頁的分頁標題都經過這裡，同時開著測試站與正式站時，
 * 光看分頁就分得出來。
 */
export const pageTitle = (page?: string) => {
  const title = page ? `${page} — ${PLATFORM_NAME}` : PLATFORM_NAME
  const label = envLabel()
  return label ? `[${label}] ${title}` : title
}
