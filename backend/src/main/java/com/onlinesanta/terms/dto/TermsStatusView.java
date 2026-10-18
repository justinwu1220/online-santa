package com.onlinesanta.terms.dto;

import java.time.Instant;

import com.onlinesanta.terms.TermsType;

/**
 * 某份須知對目前使用者的狀態。
 *
 * @param currentVersion  目前的版本
 * @param acceptedVersion 最近一次同意的版本，從沒同意過為 null（可能比目前版本舊）
 * @param acceptedAt      最近一次同意的時間
 * @param upToDate        最近一次同意的就是目前版本，不需要再同意
 */
public record TermsStatusView(
        TermsType type,
        String title,
        String currentVersion,
        String acceptedVersion,
        Instant acceptedAt,
        boolean upToDate) {
}
