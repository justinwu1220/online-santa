package com.onlinesanta.job.dto;

import java.time.Instant;

/**
 * 一次 PENDING 附件清理的結果。
 *
 * <p>回傳明細而非只回 200，理由同 {@link ReleaseSweepResult}：Cloud Scheduler
 * 的執行紀錄會保留回應內容，出事時這是唯一能回溯「那天到底清了幾筆」的地方。
 *
 * <p>{@code found} 與 {@code deleted + failed} 理論上相等；不相等代表程式邏輯
 * 有漏掉的分支，值得查。
 *
 * @param cutoff  這次掃描的門檻時間（createdAt 早於此時間的 PENDING 附件才算孤兒）
 * @param found   掃到的孤兒附件總數
 * @param deleted 成功清除的筆數（含儲存端物件與 DB 列）
 * @param failed  清除失敗、被略過的筆數——單筆失敗不擋整批，這裡是清垃圾，
 *                部分成功有意義，因此沒有「要嘛全部要嘛不動」的一致性保證
 */
public record AttachmentCleanupResult(
        Instant cutoff,
        int found,
        int deleted,
        int failed) {

    public boolean hasWork() {
        return found > 0;
    }
}
