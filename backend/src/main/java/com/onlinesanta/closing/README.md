# closing：捐贈收據與結案文件

活動結案要做兩件事：開收據給捐贈人、產生報送主管機關的文件。這個套件負責這些，資料來源是**已開立的收據快照**
（`donation_receipts`，開立後不可修改，要改就作廢重開）。

## 觀念

- **檔期（Campaign）**：一個活動期間（勸募期間、財物使用期間、願望上架期間、收據前綴、許可文號）。
  統計與收據歸屬都以「實際收到日落在 `[本檔期開始, 下一檔期開始)`」為準。
- **收據**：認領完成且機構驗收後自動開立，編號 `OS2026-000001`。三聯單：**紅聯給捐贈人（寄信）；
  白聯、黃聯由協會留存（只在管理員匯出提供）**。PDF 由 `pdf/ReceiptPdfRenderer` 以範本 PDF 為底、
  向量繪字產生（`GlyphOutlines`，不依賴系統字型）。
- **結案文件包**：`ClosingExportService.startPackage` 建立匯出、前端反覆呼叫 `advance`（每次約 15 秒預算），
  由 `doc/ClosingPackageBuilder` 組出壓縮檔，階段 C（成果備查）、D（結案備查）、CD（併報）。

## 檔案地圖（`doc/`）

| 類別 | 用途 |
|---|---|
| `ClosingDataService`／`ClosingDataset` | 唯讀取出範圍內的收據、機構分組、每月統計（物資） |
| `OfficialExcel`／`XlsxTemplateFiller` | 填官方 Excel 範本（沒有 POI，直接改 sheet XML） |
| `PdfTableWriter`／`PdfFlow` | 簽章用清冊 PDF 與共用排版 |
| `ReportPdfWriter`／`ClosingNarrative` | 成果報告與「說明」文字（缺資料寫【待確認】） |
| `OrgFormPdfWriter` | 05A／05B／05C（不含個人資料，姓名證號地址留白） |
| `OperationPageFiller` | 照表操作 HTML 預填（jsoup），欄位備妥才啟用複製 |
| `FacebookPackageBuilder` | 公開徵信貼文與 2160px JPG（不含證號、地址、收據編號、孩子資料） |
| `RegistryWorkbook` | 結案登記簿 xlsx |

寄信在 `ClosingMailService`（預覽 → 確認 → 分段寄出，不重寄）。

## 規則（改之前先看）

1. **不編造**：系統不知道的（個人實際領受、議決、簽章）寫「待補」或「【待確認】」。
   **本活動沒有現金捐款**（需求方已確認），所以現金、利息、自籌、現金實支在所有文件固定為 0，
   沒有財務資料可輸入；所得與支出就是物資時價，賸餘 0。
2. 簽章、用印一律留白。
3. 公開輸出只放公開名稱、日期、物資、數量、時價、機構名稱（見 `docs/PRIVACY.md`「結案文件與公開徵信」）。
4. 收據不可改；錯了就作廢重開，新編號。
5. 結案暫存檔 30 天由排程清除（`ClosingCleanupService`）。

## 待辦

- 所有 PDF 內建全字庫正楷體 TW-Kai 子集（協會選定）；要換字型見 `resources/closing/README.md` 與 `pdf/tools/README.md`。
