package com.onlinesanta.suspension;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.onlinesanta.common.dto.FieldChange;
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

    /**
     * 停權之後機構改了什麼。
     *
     * <p>資料來源已改為 profile_change_logs（原本是 organization_revisions），所以
     * {@code changes} 從「欄位 → {before, after}」的物件改成陣列。
     *
     * @param changes 有變動的欄位（機器欄位名，中文標籤在前端）
     */
    public record Revision(List<FieldChange> changes, Instant at) {
    }
}
