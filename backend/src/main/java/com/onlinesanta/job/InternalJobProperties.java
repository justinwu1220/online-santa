package com.onlinesanta.job;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 內部排程端點的設定（{@code app.internal-jobs.*}）。
 *
 * @param audience                Cloud Scheduler 在 OIDC token 中帶的 audience，
 *                                設定為本服務的網址
 * @param schedulerServiceAccount 允許觸發排程的服務帳號 email。只有它簽出的 token 會被接受
 */
@ConfigurationProperties(prefix = "app.internal-jobs")
public record InternalJobProperties(String audience, String schedulerServiceAccount) {
}
