# 視覺風格

大眾平台使用「深色玻璃」（night glass）風格，取自先前一個 Google Apps Script 版本的
聖誕送禮頁面。這份文件記錄那套風格的組成，讓之後要轉換的頁面不必重新推導。

---

## 一條界線：只有大眾平台用深色

| 區域 | 風格 | 理由 |
|---|---|---|
| 大眾平台（`/`、`/wishes/:id`、`/me/**`） | **深色玻璃** | 一次性的情感體驗。訪客停留幾分鐘，要的是氣氛與轉換 |
| 機構後台（`/org/**`） | 亮色 | 工作介面。機構會在這裡待很久，處理表格與清單 |
| 監控中心（`/admin/**`） | 亮色 | 同上，而且資料密度更高 |

深色玻璃的代價是**資料密度與長時間閱讀的舒適度**——半透明背景、低對比的次要文字，
在瀏覽十筆卡片時很美，在核對三十筆認領時很痛苦。不要因為「一致性」把後台一起改深。

### 技術上怎麼分隔

`body` 維持亮色（`bg-santa-50`）。大眾平台**不改 body**，而是自己鋪一層
`fixed inset-0 -z-10` 的深色背景（`.night-backdrop`）蓋過去。三個區域共用同一個
`body`，改 body 會讓後台一起變黑。

`PublicLayout` 與 `LoginPage` 的根元素掛 `.theme-night`，共用元件（`Form`、
`Feedback`、`Pagination`、`AuthPanel`）在深色底上的修正就寫成
`.theme-night .field-control { … }` 這種容器決定的規則——元件本身只加不影響外觀的
hook class（`field-control`、`field-label`、`btn-secondary`、`surface-muted`…），
後台看到的還是原本的樣子。

### Tailwind v4 的坑之二：覆寫必須寫在 layer 外面

**這些 `.theme-night` 規則刻意不放進 `@layer components`。**

Tailwind v4 把 utilities 放在比 components 更後面的 cascade layer，而 CSS 的分層規則是
**後面的 layer 整層勝過前面的，與特異性無關**。所以寫在 components 裡的
`.theme-night .field-control { background: … }` 會被元件自己的 `bg-white` utility 蓋掉，
再怎麼加選擇器都沒用——願望牆的分類下拉一直是白的就是這個原因。

未分層（unlayered）的規則優先於所有分層規則，所以覆寫寫在 `index.css` 檔案尾端、
不包在任何 `@layer` 裡。改動這一段時不要順手把它「整理」進 layer。

---

## 組成

### 色彩

```
底層    night-900 → night-950 的直向漸層
        + 頂部一圈 blue-900/35 的橢圓輝光（radial-gradient at top）
表面    white/5，邊框 white/10；hover 時各加一階到 /10 與 /20
主文字  white
次文字  slate-300
弱文字  slate-400 → slate-500
強調綠  emerald-500/600（主要按鈕）、emerald-200/300（連結、正向標籤）
強調紅  red-300/400、red-500/15（**只用於危險與逾期**）
強調黃  amber-200/300（進行中、提醒）
```

紅與綠是聖誕色，但**在深色底上要用淺階**（`red-300`／`emerald-300`）而不是原本亮色
主題的 `santa-700`——深底配深色字讀不到。

**紅色只代表危險。** 主要按鈕一律用綠色（亮色版的 `btn-primary` 本來就是 `santa-600`
深綠）。紅色留給破壞性操作（`btn-danger`）、逾期與錯誤，使用者掃過畫面時才讀得出
「紅色 = 要小心」。把送出、傳送這種按鈕做成紅色會稀釋掉這個訊號。

### 玻璃卡片

整套風格的主角。`index.css` 裡有四個現成的 class：

| Class | 用途 |
|---|---|
| `.glass-card` | 靜態容器（篩選列、面板） |
| `.glass-card-interactive` | 可點擊的卡片，hover 時上浮 1.5、加深背景與邊框 |
| `.glass-inset` | 卡片內的次級區塊，比外層再淡一階 |
| `.night-chip` | 小標籤 |

輸入框不需要自己的 class——`Form.tsx` 的元件已經掛了 `field-control`，
在 `.theme-night` 底下會自動變深色。

配方是 `bg-white/5` + `border-white/10` + `backdrop-blur-md` + 大圓角。

> **Tailwind v4 的坑之一**：`@apply` 不能套用同一個 `@layer` 裡自訂的 class，會得到
> `Cannot apply unknown utility class`。`.glass-card-interactive` 因此是把
> `.glass-card` 的內容展開寫，不是 `@apply glass-card`。

### 圓角尺度

```
rounded-3xl (24px)  卡片、對話框
rounded-2xl (16px)  卡片內的區塊
rounded-xl  (12px)  輸入框、按鈕
rounded-full        標籤、頭像、通知
```

圓角偏大是這套風格的識別特徵之一，配上圓體字才成立。縮小圓角會讓它變成一般的
深色主題。

### 字體

`Zen Maru Gothic`（圓體），透過 `font-rounded` 使用。

> **它是日文字體。** 繁中特有字（「臺」「峯」之類）會落到後備字體，字重與造型會有
> 落差。後備堆疊挑了 Noto Sans TC / PingFang TC / 微軟正黑體，落差不明顯但存在。
> 要完全一致得換成有完整繁中字符集的圓體，那類字體檔案通常很大（數 MB），
> 對首屏是實質成本。目前的取捨是接受少數字的落差。

### 狀態標籤與提示：兩種主題要用相反的做法

亮色版是「**淺底 + 深字**」（`bg-amber-100` + `text-amber-800`）。深色底上必須反過來成
「**半透明色底 + 淺字**」（`bg-amber-400/15` + `text-amber-200`），並加一圈同色系細邊框，
標籤才在玻璃上站得住。

直接沿用亮色版會同時犯兩個錯：`bg-amber-100` 變成刺眼的亮斑，`text-amber-800` 則完全
讀不到——這正是「有些過於亮眼、有些過於黯淡」的來源。

| tone | 深色底上的配色 |
|---|---|
| `neutral` | `bg-white/10` + `text-slate-200` |
| `positive` | `bg-emerald-400/15` + `text-emerald-200` |
| `progress` | `bg-amber-400/15` + `text-amber-200` |
| `warning` | `bg-red-500/15` + `text-red-200` |
| `muted` | `bg-white/5` + `text-slate-400` |

`Notice` 與 `ErrorBanner` 同理，都已經有對應的 `.theme-night` 規則。

### 漸層文字

標題用 `bg-gradient-to-r from-red-300 via-white to-emerald-300` +
`bg-clip-text text-transparent`。**只用在招攬性質的門面**——用多了會廉價。

目前只有頁首品牌與願望牆的主標題在用。「我的認領」這種使用者查自己東西的頁面刻意
用單純的白色標題，安靜一點比較好讀。

### 動態

| 效果 | 說明 |
|---|---|
| 飄雪 | `Snowfall` 元件。純裝飾，位置在模組載入時算一次 |
| 卡片上浮 | `hover:-translate-y-1.5` + `hover:shadow-2xl`，300ms |
| 按鈕縮放 | `hover:scale-[1.03]`，幅度要小 |

飄雪在 `prefers-reduced-motion: reduce` 時直接不顯示（`index.css` 的 `.snowflake`
規則）。它不承載資訊，關掉沒有損失。

### 按鈕

`Button` 的三個 variant 在 `.theme-night` 底下都已經有對應樣式，直接用即可：

| variant | 深色底上的樣子 |
|---|---|
| `primary` | `emerald-500 → emerald-600` 漸層 + `ring-emerald-400/30` |
| `secondary` | 玻璃：`bg-white/5` + `border-white/15` |
| `ghost` | 只有文字，hover 時 `bg-white/10` |
| `danger` | 紅色玻璃：`bg-red-500/20` + `border-red-400/40` |

> primary 用 `emerald` 而不是品牌的 `santa-500/600`，是因為後者在近黑的底上太暗，
> 按鈕會糊進背景。色相仍在同一家族。

**在半透明的頁首／頁尾上不要放實心飽和色的按鈕。** 頁首的「登入 / 註冊」是連結不是
`Button`，做成同材質的綠色玻璃（`bg-emerald-500/15` + `border-emerald-400/30` +
`text-emerald-100`），才不會從那層玻璃上浮出來。

一個畫面上只留一顆飽和的按鈕，其餘都用玻璃或 ghost。

---

## 已套用與待套用

| 頁面 | 狀態 |
|---|---|
| `PublicLayout`（頁首／頁尾／背景） | ✅ |
| `WishWall`（願望牆） | ✅ |
| `LoginPage`（大眾登入） | ✅ |
| `Form`（Field / TextInput / Select / Button） | ✅ 透過 `.theme-night` 容器規則 |
| `Feedback` / `Pagination` | ✅ 同上 |
| `AuthPanel`（分頁列、錯誤文字） | ✅ 同上 |
| `MyClaims`（我的認領） | ✅ |
| `ClaimDetail`（認領詳情） | ✅ |
| `StatusBadge` / `Notice` / `ErrorBanner` | ✅ 透過 `.theme-night` 容器規則 |
| `Timeline` / `MessageThread` | ✅ 同上 |
| `WishDetail`（願望詳情） | ✅ |
| `Breadcrumb` | ✅ 同上 |

**大眾平台已全部轉換完成。**

日後新增大眾平台的頁面時，成本已經被攤平了：表單、按鈕、標籤、提示、分頁、歷程、
對話、麵包屑在深色底上都會自動正確，只要把頁面自己的 `bg-white` 面板換成
`.glass-card`、把 `text-slate-800` 之類的深色文字換成淺階即可。

### 詳情頁一律用麵包屑

`Breadcrumb` 元件。願望與認領的連結**常被分享出去**——有人是從別人傳來的網址直接落
在詳情頁的，沒有瀏覽器上一頁可回。麵包屑同時說明「你在哪一層」與「怎麼往上走」，
比一個孤零零的「← 返回」好。

最後一項是目前頁面，用 `aria-current="page"` 標記而不是做成連結。

### 文字對比的分級

深色底上不要只用兩階（白／灰），會失去層次。目前用四階：

| 用途 | 顏色 |
|---|---|
| 要照著抄的資訊（寄送地址、期限日期） | `text-white` + `font-semibold` |
| 標題 | `text-white` |
| 正文 | `text-slate-300` |
| 次要說明、欄位標籤 | `text-slate-400` |
| 時間戳、極次要 | `text-slate-500` |

`slate-500` 是可讀性的下限，只用在時間戳這種「知道有就好」的資訊，不要拿它放正文。

**新增元件時的原則**：不要在呼叫點寫一長串深色 class，也不要把深色寫死進共用元件
（後台會一起變黑）。加一個 hook class，把差異寫進 `index.css` 尾端的 `.theme-night`
區塊。
