package com.onlinesanta.claim.dto;

import java.time.Instant;
import java.util.UUID;

import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimStatus;
import com.onlinesanta.organization.ReleasePolicy;

/**
 * 機構檢視自己願望的認領狀況。
 *
 * <p>含捐贈者的聯絡資訊——機構需要能主動聯繫遲遲未寄送的人，這是流程必要而非
 * 額外蒐集。相對地，捐贈者那一側的視圖不會看到其他捐贈者的任何資料。
 */
public record ClaimOrgView(
        UUID id,
        ClaimStatus status,
        UUID wishId,
        String wishTitle,
        String childAlias,
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
        String donorMessage,
        String releaseReason) {

    public static ClaimOrgView from(Claim claim) {
        return new ClaimOrgView(
                claim.getId(),
                claim.getStatus(),
                claim.getWish().getId(),
                claim.getWish().getTitle(),
                claim.getWish().getChildAlias(),
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
                claim.getDonorMessage(),
                claim.getReleaseReason());
    }
}
