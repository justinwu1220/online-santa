package com.onlinesanta.job.dto;

import java.time.Instant;

/**
 * 一次管理員每日待審摘要排程的結果。
 *
 * @param sweptAt        執行時間
 * @param totalPending   四類待審的總件數；0 代表沒有東西要審，整封信都不寄
 * @param recipients     符合收信條件的管理員人數
 * @param sent           成功寄出的封數
 * @param alreadySent    今天（台灣日期）已經寄過、這次略過的人數
 * @param failed         寄送失敗的人數（不會被記成已寄，下次排程會再試）
 */
public record AdminDigestResult(Instant sweptAt, int totalPending, int recipients,
                                int sent, int alreadySent, int failed) {
}
