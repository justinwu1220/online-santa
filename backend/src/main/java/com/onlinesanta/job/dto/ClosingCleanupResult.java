package com.onlinesanta.job.dto;

import java.time.Instant;

/**
 * 一次結案文件暫存檔清除的結果。回傳明細的理由同 {@link AttachmentCleanupResult}：Cloud Scheduler
 * 的執行紀錄是事後回溯「那天清了什麼」的唯一地方。
 *
 * @param receiptsFound   暫存期限已過的收據 PDF 筆數
 * @param receiptsCleared 其中成功清除的筆數
 * @param exportsFound    暫存期限已過的匯出壓縮檔筆數
 * @param exportsCleared  其中成功清除的筆數
 * @param failed          清除失敗的筆數（下次排程會再試）
 */
public record ClosingCleanupResult(Instant ranAt, int receiptsFound, int receiptsCleared,
                                   int exportsFound, int exportsCleared, int failed) {
}
