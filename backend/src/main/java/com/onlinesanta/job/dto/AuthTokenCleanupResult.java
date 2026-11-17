package com.onlinesanta.job.dto;

/**
 * 一次登入相關 token 清理的結果。回傳明細的理由同 {@link AttachmentCleanupResult}：
 * Cloud Scheduler 的執行紀錄會保留回應內容。
 *
 * @param authTokensDeleted    刪掉的驗證碼與重設密碼 token 筆數
 * @param refreshTokensDeleted 刪掉的登入狀態筆數
 */
public record AuthTokenCleanupResult(int authTokensDeleted, int refreshTokensDeleted) {
}
