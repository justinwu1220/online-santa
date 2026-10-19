package com.onlinesanta.wish.dto;

import java.time.Instant;
import java.util.List;

/**
 * 重新送審的願望與「上次核准的版本」相比改了什麼。
 *
 * @param lastApprovedAt 上次核准的時間
 * @param fields         文字欄位的變更（舊 → 新）；沒變的欄位不列
 * @param lettersBefore  上次核准時的信件照片張數
 * @param lettersAfter   現在的信件照片張數
 * @param lettersChanged 信件照片有沒有更動（換了照片張數可能一樣）
 */
public record WishChanges(
        Instant lastApprovedAt,
        List<FieldChange> fields,
        int lettersBefore,
        int lettersAfter,
        boolean lettersChanged) {

    public record FieldChange(String field, String before, String after) {
    }
}
