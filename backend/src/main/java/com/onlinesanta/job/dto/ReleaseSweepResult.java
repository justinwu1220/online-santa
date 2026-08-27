package com.onlinesanta.job.dto;

import java.time.Instant;

/**
 * 一次逾期掃描的結果。
 *
 * <p>回傳明細而非只回 200：Cloud Scheduler 的執行紀錄會保留回應內容，出事時
 * 這是唯一能回溯「那天到底釋回了幾筆」的地方。
 *
 * @param sweptAt                掃描時間
 * @param overdueFound           掃到的逾期認領總數
 * @param autoReleased           依 AUTO 政策自動釋回的筆數
 * @param wishesReturnedToWall   實際回到願望牆的願望數。正常應等於 autoReleased；
 *                               不相等代表有願望的狀態與認領不同步，值得查
 * @param flaggedForOrganization MANUAL 政策、僅標記待機構處理的筆數
 */
public record ReleaseSweepResult(
        Instant sweptAt,
        int overdueFound,
        int autoReleased,
        int wishesReturnedToWall,
        int flaggedForOrganization) {

    public boolean hasWork() {
        return overdueFound > 0;
    }
}
