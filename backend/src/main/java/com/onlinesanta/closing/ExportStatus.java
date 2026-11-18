package com.onlinesanta.closing;

/** 匯出作業狀態。 */
public enum ExportStatus {
    /** 補產 PDF 與打包中，前端輪詢 advance 推進。 */
    PREPARING,
    /** 壓縮檔已就緒，可下載。 */
    READY,
    /** 失敗，原因在 error。 */
    FAILED,
    /** 壓縮檔已過期被清除，需要時重新匯出。 */
    EXPIRED
}
