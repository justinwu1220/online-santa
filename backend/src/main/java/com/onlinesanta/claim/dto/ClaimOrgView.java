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
 * <p><strong>刻意不含</strong>認領者的信箱與電話、禮物價格（設計稿 15.4，《合作機構參與須知》
 * 第七節）：機構與認領者只透過站內對話聯繫；禮物價格是認領者回報寄送時給收據用的資料，只有管理員看得到。
 * 型別分離而不是把欄位清成 null，這樣機構端不可能因為漏判而洩漏。
 */
public record ClaimOrgView(
        UUID id,
        ClaimStatus status,
        UUID wishId,
        String wishTitle,
        String childAlias,
        String donorName,
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
                claim.getShippedOn(),
                claim.getDonorMessage(),
                claim.getReleaseReason(),
                unreadMessageCount);
    }
}
