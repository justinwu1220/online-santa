package com.onlinesanta.claim.dto;

import java.time.Instant;
import java.util.UUID;

import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimStatus;
import com.onlinesanta.wish.Wish;

/**
 * 捐贈者檢視自己的認領。
 *
 * <p>帶上願望與機構的基本資訊，讓「我的認領」頁面不必再逐筆去查願望。
 */
public record ClaimDonorView(
        UUID id,
        ClaimStatus status,
        UUID wishId,
        String wishTitle,
        String childAlias,
        String organizationName,
        Instant claimedAt,
        Instant shipDeadlineAt,
        boolean overdue,
        Instant shippedAt,
        Instant receivedAt,
        Instant completedAt,
        String trackingCarrier,
        String trackingNumber,
        String donorMessage,
        String releaseReason) {

    public static ClaimDonorView from(Claim claim) {
        Wish wish = claim.getWish();
        return new ClaimDonorView(
                claim.getId(),
                claim.getStatus(),
                wish.getId(),
                wish.getTitle(),
                wish.getChildAlias(),
                wish.getOrganization().getName(),
                claim.getClaimedAt(),
                claim.getShipDeadlineAt(),
                claim.isOverdue(Instant.now()),
                claim.getShippedAt(),
                claim.getReceivedAt(),
                claim.getCompletedAt(),
                claim.getTrackingCarrier(),
                claim.getTrackingNumber(),
                claim.getDonorMessage(),
                claim.getReleaseReason());
    }
}
