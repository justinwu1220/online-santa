package com.onlinesanta.wish.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimStatus;

/**
 * 願望目前佔用中的那一筆認領的摘要（機構後台清單與抽屜用）。
 * 刻意沒有認領者的信箱、電話與禮物價格（設計稿 15.4）。
 */
public record ActiveClaimSummary(
        UUID claimId,
        ClaimStatus status,
        String donorName,
        Instant claimedAt,
        Instant shipDeadlineAt,
        boolean overdue,
        Instant shippedAt,
        Instant receivedAt,
        LocalDate receivedOn,
        Instant completedAt) {

    public static ActiveClaimSummary from(Claim claim, Instant now) {
        return new ActiveClaimSummary(
                claim.getId(),
                claim.getStatus(),
                claim.getDonor().getDisplayName(),
                claim.getClaimedAt(),
                claim.getShipDeadlineAt(),
                claim.isOverdue(now),
                claim.getShippedAt(),
                claim.getReceivedAt(),
                claim.getReceivedOn(),
                claim.getCompletedAt());
    }
}
