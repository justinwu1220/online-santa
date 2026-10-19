package com.onlinesanta.wish.dto;

import java.time.Instant;
import java.util.UUID;

import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimStatus;

/** 願望過去被收回或取消的認領紀錄（不是消失，抽屜裡留著「過往認領紀錄」）。 */
public record PastClaimView(
        UUID claimId,
        ClaimStatus status,
        String donorName,
        Instant claimedAt,
        Instant endedAt,
        String reason) {

    public static PastClaimView from(Claim claim) {
        return new PastClaimView(claim.getId(), claim.getStatus(), claim.getDonor().getDisplayName(),
                claim.getClaimedAt(), claim.getReleasedAt(), claim.getReleaseReason());
    }
}
