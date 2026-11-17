import { envLabel } from '../lib/brand'

/**
 * 非正式環境的醒目提示橫幅（例如「測試站」）。正式站沒有設定 `VITE_ENV_LABEL`，這裡什麼都不渲染。
 *
 * 測試站與正式站的畫面幾乎一樣，最危險的誤會是「以為自己在正式站」或「以為這裡是測試站」：
 * 在測試站填了真實個資、或在正式站做了只想在測試站做的操作。所以橫幅要大、要黃黑相間、
 * 要黏在頂端（捲動時也在）、每一頁都有，登入前就看得到。
 *
 * 樣式刻意全部寫在元件裡，不依賴 `.rd` 底下的全站樣式：登入頁、重設密碼頁有自己的全螢幕版面，
 * 橫幅放在所有路由之外，不能因為某個版面的樣式範圍而失效。
 */
export function EnvironmentBanner() {
  const label = envLabel()
  if (!label) return null

  return (
    <div
      role="note"
      aria-label={`目前是${label}`}
      data-testid="environment-banner"
      style={{
        position: 'sticky',
        top: 0,
        // 比一般內容高、比對話框低：對話框蓋住它沒關係，它不該蓋住下拉選單與提示
        zIndex: 900,
        display: 'flex',
        flexWrap: 'wrap',
        alignItems: 'center',
        justifyContent: 'center',
        gap: '4px 12px',
        padding: '8px 16px',
        // 黃黑斜紋：警示標誌的視覺語言，和網站其餘的深色夜空與柔和配色明顯不同
        background:
          'repeating-linear-gradient(-45deg, #facc15 0, #facc15 14px, #eab308 14px, #eab308 28px)',
        color: '#1c1917',
        borderBottom: '2px solid #1c1917',
        fontSize: 14,
        lineHeight: 1.5,
        textAlign: 'center',
      }}
    >
      <strong style={{ fontSize: 16, letterSpacing: '.08em' }}>⚠ 這裡是{label}</strong>
      <span>資料會不定期清除；信件與驗證碼僅供測試，請勿填入真實個資。</span>
    </div>
  )
}
