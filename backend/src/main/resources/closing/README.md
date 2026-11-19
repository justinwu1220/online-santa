# 結案文件的資源檔

| 檔案 | 來源 | 說明 |
|---|---|---|
| `fonts/TW-Kai-Subset.ttf` | 全字庫正楷體 TW-Kai（國家發展委員會／數位發展部，版本 11510.1，2026-10 版），取自 data.cns11643.gov.tw | 以 `BuildKaiSubset`（PDFBox TTFSubsetter）裁成 Big5 全部字（常用與次常用約 1.3 萬字）、ASCII、全形標點、注音，約 19MB。授權可選 SIL OFL 1.1 或政府資料開放授權 1.0（字型 name 表記載），見 `fonts/OFL.txt`。每張收據中「每張不同」的內容（姓名、品項、金額…）與其他結案 PDF 的文字都用它的字形輪廓畫出。字型沒有的字畫成「？」並列入待補資料。 |
| `receipt-template.pdf` | 使用者提供的「康乃心_活動捐贈收據_三聯空白版.pdf」，經 `BuildReceiptTemplate` 工具處理 | 三頁（白、黃、紅）。Logo 重新壓縮（320px 寬、Flate）；原檔文字全部清掉，固定標籤改用全字庫正楷體（只含需要的 191 個字的小字型，約 0.28MB，整份嵌入）以真文字寫回原位置；預印的舊編號、舊許可文號與期間兩行、數量欄「份」已移除 |

## 為什麼動態內容用字形輪廓、不嵌入字型

PDFBox 3.0.5／3.0.6 對這類（Noto、楷體）字型做**子集化嵌入**會產生錯字形（實測：整份嵌入正常，但每份數 MB 到數十 MB；子集化則亂碼）。所以：

- 固定標籤：事先用 BuildKaiSubset 裁好小字型，再用 PDFBox「不子集化、整份嵌入」（小字型才 0.3MB）。
- 動態內容：直接把字形畫成向量路徑，每個字形在一份 PDF 裡只定義一次（表單物件），三頁共用。

## 重新產生範本

見 `backend/src/test/java/com/onlinesanta/closing/pdf/tools/README.md`。
