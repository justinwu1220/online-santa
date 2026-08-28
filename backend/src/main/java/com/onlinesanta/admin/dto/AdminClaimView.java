package com.onlinesanta.admin.dto;

import java.time.Instant;
import java.util.UUID;

import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimStatus;
import com.onlinesanta.organization.ReleasePolicy;

/**
 * 管理員的跨機構認領檢視。
 *
 * <p>含捐贈者的姓名與 email——處理申訴時需要辨識當事人。正因為如此，取得單筆詳情
 * 會寫入稽核紀錄；清單頁則只用於掌握整體流程狀況。
 */
public record AdminClaimView(
        UUID id,
        ClaimStatus status,
        UUID wishId,
        String wishTitle,
        String childAlias,
        UUID organizationId,
        String organizationName,
        String donorName,
        String donorEmail,
        Instant claimedAt,
        Instant shipDeadlineAt,
        boolean overdue,
        ReleasePolicy releasePolicySnapshot,
        Instant shippedAt,
        Instant receivedAt,
        Instant completedAt,
        String trackingCarrier,
        String trackingNumber,
        String releaseReason) {

    public static AdminClaimView from(Claim claim) {
        return new AdminClaimView(
                claim.getId(),
                claim.getStatus(),
                claim.getWish().getId(),
                claim.getWish().getTitle(),
                claim.getWish().getChildAlias(),
                claim.getWish().getOrganization().getId(),
                claim.getWish().getOrganization().getName(),
                claim.getDonor().getDisplayName(),
                claim.getDonor().getEmail(),
                claim.getClaimedAt(),
                claim.getShipDeadlineAt(),
                claim.isOverdue(Instant.now()),
                claim.getReleasePolicySnapshot(),
                claim.getShippedAt(),
                claim.getReceivedAt(),
                claim.getCompletedAt(),
                claim.getTrackingCarrier(),
                claim.getTrackingNumber(),
                claim.getReleaseReason());
    }
}
