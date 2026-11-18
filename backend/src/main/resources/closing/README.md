# 結案文件的資源檔

| 檔案 | 來源 | 說明 |
|---|---|---|
| `fonts/LXGWWenKaiTC-Subset.ttf` | 霞鶩文楷 TC（LXGW WenKai TC）Regular，取自 google/fonts `ofl/lxgwwenkaitc` | 以 fontTools 裁成常用範圍（拉丁、標點、注音、CJK 統一表意文字與擴充 A、相容表意文字、直排標點、全形）。授權 SIL OFL 1.1，見 `fonts/OFL.txt`；無保留字型名稱。每張收據中「每張不同」的內容（姓名、品項、金額…）以這個字型的字形輪廓繪製 |
| `receipt-template.pdf` | 使用者提供的「康乃心_活動捐贈收據_三聯空白版.pdf」，經 `BuildReceiptTemplate` 工具處理 | 三頁（白、黃、紅）。Logo 重新壓縮（320px 寬、Flate）；原檔文字全部清掉，固定標籤改用霞鶩文楷（只含需要的 192 個字的小字型，整份嵌入）以真文字寫回原位置；預印的舊編號、舊許可文號與期間兩行、數量欄「份」已移除 |

## 為什麼動態內容用字形輪廓、不嵌入字型

PDFBox 3.0.5／3.0.6 對這類（Noto、霞鶩文楷）字型做**子集化嵌入**會產生錯字形（實測：整份嵌入正常，但每份 5MB；子集化則亂碼）。所以：

- 固定標籤：事先用 fontTools 裁好小字型，再用 PDFBox「不子集化、整份嵌入」（小字型才 100KB）。
- 動態內容：直接把字形畫成向量路徑，每個字形在一份 PDF 裡只定義一次（表單物件），三頁共用。

## 重新產生範本

見 `backend/src/test/java/com/onlinesanta/closing/pdf/tools/README.md`。
