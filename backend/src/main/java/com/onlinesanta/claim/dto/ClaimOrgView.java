package com.onlinesanta.claim.dto;

import java.time.Instant;
import java.time.LocalDate;
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
        /** 機構填的實際收到日期（和 receivedAt 這個確認時間分開）；舊資料沒有 */
        LocalDate receivedOn,
        Instant completedAt,
        String trackingCarrier,
        String trackingNumber,
        String giftDescription,
        Integer giftQuantity,
        Integer giftPrice,
        LocalDate shippedOn,
        String donorMessage,
        String releaseReason,
        long unreadMessageCount) {

    public static ClaimOrgView from(Claim claim) {
        return from(claim, 0);
    }

    public static ClaimOrgView from(Claim claim, long unreadMessageCount) {
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
                claim.getReceivedOn(),
                claim.getCompletedAt(),
                claim.getTrackingCarrier(),
                claim.getTrackingNumber(),
                claim.getGiftDescription(),
                claim.getGiftQuantity(),
                claim.getGiftPrice(),
                claim.getShippedOn(),
                claim.getDonorMessage(),
                claim.getReleaseReason(),
                unreadMessageCount);
    }
}
