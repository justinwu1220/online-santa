package com.onlinesanta.admin.dto;

import java.time.Instant;
import java.util.UUID;

import com.onlinesanta.changelog.ProfileChangeTargetSummary;
import com.onlinesanta.changelog.ProfileChangeTargetType;

/**
 * 「有異動紀錄的對象」清單的一列。
 *
 * <p>只到「改過幾次、最近一次什麼時候」，不含任何欄位值——實際改了什麼要開抽屜，
 * 而開抽屜會寫一筆稽核。
 *
 * @param label 認領者的信箱或機構名稱；對象已被刪除時為 null，前端顯示 id
 */
public record ProfileChangeTargetView(
        ProfileChangeTargetType targetType,
        UUID targetId,
        String label,
        long changeCount,
        Instant lastChangedAt) {

    public static ProfileChangeTargetView from(ProfileChangeTargetSummary summary, String label) {
        return new ProfileChangeTargetView(summary.targetType(), summary.targetId(), label,
                summary.changeCount(), summary.lastChangedAt());
    }
}
