package com.onlinesanta.job.dto;

import java.time.Instant;

/**
 * 一次寄送期限提醒排程的結果。
 *
 * @param sweptAt 掃描時間
 * @param found   落在提醒窗口內、還沒寄過提醒的認領數
 * @param sent    成功寄出提醒的筆數
 * @param failed  失敗、被略過的筆數——逐筆處理，單筆失敗不擋整批
 */
public record DeadlineReminderResult(Instant sweptAt, int found, int sent, int failed) {

    public boolean hasWork() {
        return found > 0;
    }
}
