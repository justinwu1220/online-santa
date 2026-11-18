package com.onlinesanta.closing;

/** 收據 PDF 的狀態。 */
public enum PdfStatus {
    /** 還沒產生（剛開立，或暫存已過期被清除，需要時再產生）。 */
    PENDING,
    /** 檔期尚缺許可文號，不產生正式 PDF；補上文號後可批次補產。 */
    PENDING_INFO,
    /** 已產生並暫存在雲端。 */
    READY,
    /** 版面放不下、字型缺字等，原因記在 pdfError，要人工處理。 */
    FAILED
}
