package com.onlinesanta.suspension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.onlinesanta.claim.ClaimStatus;

/**
 * 管理員看單一認領者：基本資料、進行中的認領（含目前的限制與能否釋出），以及停權當下保全的資訊快照。
 *
 * @param snapshot 停權當下的資訊（JSON）；沒有進行中的停權紀錄為 null
 */
public record AdminUserDetailView(
        AdminUserView user,
        String phone,
        List<ClaimItem> claims,
        JsonNode snapshot) {

    /**
     * @param restriction ClaimRestriction 名稱
     * @param canRelease  只有還沒寄出的認領能由管理員釋出
     */
    public record ClaimItem(
            UUID claimId,
            UUID wishId,
            String wishTitle,
            String organizationName,
            ClaimStatus status,
            String restriction,
            Instant claimedAt,
            boolean canRelease) {
    }
}
