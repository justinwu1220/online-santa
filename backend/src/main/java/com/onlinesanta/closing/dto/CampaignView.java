package com.onlinesanta.closing.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.onlinesanta.closing.Campaign;
import com.onlinesanta.closing.CampaignStatus;

/** 活動檔期的後台檢視。{@code nextReceiptNo} 是下一張會配出的編號預覽，不保證到時一定是它。 */
public record CampaignView(
        UUID id,
        String name,
        String activityNo,
        String permitNo,
        String approvedName,
        LocalDate collectionStart,
        LocalDate collectionEnd,
        LocalDate useStart,
        LocalDate useEnd,
        LocalDate wishListStart,
        LocalDate wishListEnd,
        String receiptPrefix,
        String nextReceiptNo,
        int receiptsIssued,
        UUID associationOrgId,
        CampaignStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static CampaignView from(Campaign c) {
        return new CampaignView(c.getId(), c.getName(), c.getActivityNo(), c.getPermitNo(), c.getApprovedName(),
                c.getCollectionStart(), c.getCollectionEnd(), c.getUseStart(), c.getUseEnd(), c.getWishListStart(),
                c.getWishListEnd(),
                c.getReceiptPrefix(), "%s-%06d".formatted(c.getReceiptPrefix(), c.getNextReceiptSeq()),
                c.getReceiptsIssued(), c.getAssociationOrgId(), c.getStatus(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
