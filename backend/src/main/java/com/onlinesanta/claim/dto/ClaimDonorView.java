package com.onlinesanta.claim.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimStatus;
import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.Wish;

/**
 * 捐贈者檢視自己的認領。
 *
 * <p>帶上願望與機構的基本資訊，讓「我的認領」頁面不必再逐筆去查願望。
 *
 * <p><strong>機構的地址與電話只在這裡出現，不在願望牆或願望詳情。</strong>
 * 認領之後才需要知道寄去哪，而 ClaimService 的擁有者檢查就是那道界線——
 * 公開視圖（WishPublicView）只有機構名稱，不要把地址加進去。
 */
public record ClaimDonorView(
        UUID id,
        ClaimStatus status,
        UUID wishId,
        String wishTitle,
        String childAlias,
        AgeRange ageRange,
        String ageRangeLabel,
        String organizationName,
        /** 機構簡介（機構設定），認領頁右欄的機構小卡顯示；沒填為 null */
        String organizationDescription,
        /** 寄送目的地。機構註冊時必填，舊資料可能為空，前端要有沒填時的說法 */
        String organizationAddress,
        String organizationPhone,
        String organizationMobilePhone,
        String organizationPreferredDeliveryTime,
        Instant claimedAt,
        Instant shipDeadlineAt,
        boolean overdue,
        Instant shippedAt,
        Instant receivedAt,
        Instant completedAt,
        String trackingCarrier,
        String trackingNumber,
        String giftDescription,
        Integer giftQuantity,
        Integer giftPrice,
        LocalDate shippedOn,
        String donorMessage,
        /** 是否申請了捐贈收據（只有旗標，收據資料不在這裡） */
        boolean receiptRequested,
        String releaseReason,
        long unreadMessageCount) {

    public static ClaimDonorView from(Claim claim) {
        return from(claim, 0);
    }

    public static ClaimDonorView from(Claim claim, long unreadMessageCount) {
        Wish wish = claim.getWish();
        return new ClaimDonorView(
                claim.getId(),
                claim.getStatus(),
                wish.getId(),
                wish.getTitle(),
                wish.getChildAlias(),
                wish.getAgeRange(),
                wish.getAgeRange().getLabel(),
                wish.getOrganization().getName(),
                wish.getOrganization().getDescription() == null
                        || wish.getOrganization().getDescription().isBlank()
                        ? null : wish.getOrganization().getDescription().trim(),
                // ClaimRepository 的 @EntityGraph 已經把 wish.organization 抓進來了，
                // 這兩個 getter 不會多打一次查詢
                wish.getOrganization().getAddress(),
                wish.getOrganization().getContactPhone(),
                wish.getOrganization().getMobilePhone(),
                wish.getOrganization().getPreferredDeliveryTime(),
                claim.getClaimedAt(),
                claim.getShipDeadlineAt(),
                claim.isOverdue(Instant.now()),
                claim.getShippedAt(),
                claim.getReceivedAt(),
                claim.getCompletedAt(),
                claim.getTrackingCarrier(),
                claim.getTrackingNumber(),
                claim.getGiftDescription(),
                claim.getGiftQuantity(),
                claim.getGiftPrice(),
                claim.getShippedOn(),
                claim.getDonorMessage(),
                claim.isReceiptRequested(),
                claim.getReleaseReason(),
                unreadMessageCount);
    }
}
