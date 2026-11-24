import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, type Plugin } from 'vite'

/**
 * 建置時把 index.html 裡的 HTML 註解拿掉。
 *
 * 那些註解是寫給工程師看的（為什麼 canonical 只有一份、og 為什麼沒有圖、標題文案的出處），
 * 沒必要跟著每一次瀏覽送給訪客，也不必出現在「檢視網頁原始碼」裡。原始檔照常留著註解，
 * 開發伺服器也不處理——除錯時看得到比較好。
 *
 * 只處理 index.html；React 渲染出來的 DOM 本來就不含註解。
 * 字串裡如果真的出現 `<!--`（目前只有一段 JSON-LD，沒有），這個規則會誤砍，加之前要先確認。
 */
function stripHtmlComments(): Plugin {
  return {
    name: 'strip-html-comments',
    apply: 'build',
    transformIndexHtml: {
      order: 'post',
      handler: (html) => html.replace(/[ \t]*<!--[\s\S]*?-->\n?/g, ''),
    },
  }
}

export default defineConfig({
  plugins: [react(), tailwindcss(), stripHtmlComments()],
  server: {
    port: 5173,
    // 開發時把 /api 與 /actuator 轉給本地後端，避免 CORS 設定分歧
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/actuator': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
})
