package com.onlinesanta.claim;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 認領相關的可調參數（{@code app.claim.*}）。
 *
 * @param maxActivePerDonor       每位民眾同時進行中的認領上限。去年願望供不應求，
 *                                沒有上限的話少數人就能把大量願望鎖住
 * @param defaultReleaseAfterDays 機構未指定時的寄送寬限天數
 */
@ConfigurationProperties(prefix = "app.claim")
public record ClaimProperties(int maxActivePerDonor, int defaultReleaseAfterDays) {
}
