package com.onlinesanta.changelog;

import java.time.Instant;
import java.util.UUID;

/**
 * 「有異動紀錄的對象」清單的一列：誰、改過幾次、最近一次什麼時候。
 *
 * <p>沒有任何紀錄的對象不會出現在這個查詢的結果裡——這份清單要回答的是「哪些人改過
 * 資料」，把全部的人都列出來只會讓真正要看的那幾筆被淹掉。
 *
 * <p>刻意不含任何欄位值：清單只到「改過幾次」，實際改了什麼要開抽屜，而開抽屜會寫稽核。
 */
public record ProfileChangeTargetSummary(
        ProfileChangeTargetType targetType,
        UUID targetId,
        long changeCount,
        Instant lastChangedAt) {
}
