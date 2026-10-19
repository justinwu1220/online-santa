import { defineConfig } from 'vitest/config'

/**
 * 測試設定（與 vite.config.ts 分開：vitest 內建自己版本的 vite，型別混在同一支設定會對不上）。
 * 元件測試用 jsdom（各測試檔以 `// @vitest-environment jsdom` 宣告）；純函式測試維持 node。
 */
export default defineConfig({
  esbuild: { jsx: 'automatic' },
  test: {
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
})
