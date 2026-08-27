package com.onlinesanta.attachment;

/** 附件的上傳狀態。對應 {@code attachments.upload_status} 的 CHECK 約束。 */
public enum UploadStatus {

    /**
     * 已發出上傳網址，但尚未向儲存端查證。
     *
     * <p>停留在這個狀態的紀錄可能永遠不會完成（使用者關掉頁面、上傳失敗），
     * 因此任何對外的查詢都只看 CONFIRMED。
     */
    PENDING,

    /** 後端已向儲存端確認檔案存在，且型別與大小符合限制。 */
    CONFIRMED
}
