package com.onlinesanta.closing;

/** 收據 PDF 的狀態。 */
public enum PdfStatus {
    /** 還沒產生（剛開立、產生時雲端暫時失敗，或暫存已過期被清除）；預覽或匯出時會自動產生。 */
    PENDING,
    /** 已產生並暫存在雲端。 */
    READY,
    /** 版面放不下、字型缺字等，原因記在 pdfError，要人工處理。 */
    FAILED
}
