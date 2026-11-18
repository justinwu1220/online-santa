# 收據範本產生工具

平常不需要執行。只有收據範本（原始 PDF）或字型要更換時才重做 `src/main/resources/closing/receipt-template.pdf`。

## 步驟

1. **Logo 壓縮**：把原始範本裡 809×701 的 Logo 縮到 320px 寬，改存單純 Flate（原本是 ASCII85＋Flate，體積約 1.25 倍）。其餘內容不動。得到 `template-320.pdf`。
2. **取出標籤**：
   ```
   java -cp <classes;test-classes;依賴> com.onlinesanta.closing.pdf.tools.BuildReceiptTemplate extract template-320.pdf 工作目錄
   ```
   產生 `labels.tsv`（每段文字的頁、位置、字級、顏色、內容）與 `chars.txt`（用到的所有字）。
3. **裁小字型**（fontTools）：以 `chars.txt` 加數字 0–9，把完整的霞鶩文楷裁成只含這些字的 `labels.ttf`（不留 GSUB/GPOS 等，約 100KB）。
4. **組範本**：
   ```
   ... BuildReceiptTemplate build template-320.pdf 工作目錄 labels.ttf 輸出.pdf
   ```
   把原文字區塊全部清掉，再用 `labels.ttf` 整份嵌入（不經 PDFBox 子集化）把每段文字寫回原位置。預印的舊編號、勸募活動／許可期間兩行與數量欄「份」不寫回（由每張收據的內容取代）。
5. 用輸出覆蓋 `closing/receipt-template.pdf`，跑 `ReceiptPdfRendererTest` 並目視 `target/receipt-preview/*.png`。

## 為什麼不直接嵌入完整字型或讓 PDFBox 子集化

見 `src/main/resources/closing/README.md`。
