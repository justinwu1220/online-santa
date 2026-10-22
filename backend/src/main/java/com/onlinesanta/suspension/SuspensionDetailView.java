package com.onlinesanta.suspension;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.onlinesanta.unlock.UnlockRequestView;

/**
 * 管理員看停權機構的處置資料：停權當下保全的原始資訊、停權期間機構修改了什麼、機構的解除申請，
 * 以及受影響的願望與認領數。
 *
 * @param snapshot 停權當下的機構資料與證明文件清單（JSON）；沒有進行中的停權紀錄為 null
 */
public record SuspensionDetailView(
        String reason,
        Instant suspendedAt,
        JsonNode snapshot,
        List<Revision> revisions,
        UnlockRequestView unlockRequest,
        long openWishes,
        long affectedClaims) {

    /** @param changes 欄位 → {before, after} */
    public record Revision(JsonNode changes, Instant at) {
    }
}
