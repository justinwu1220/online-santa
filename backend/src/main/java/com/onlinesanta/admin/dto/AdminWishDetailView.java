package com.onlinesanta.admin.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.onlinesanta.attachment.dto.AttachmentView;
import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimStatus;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishStatus;
import com.onlinesanta.wish.dto.WishChanges;
import com.onlinesanta.wish.dto.WishHistoryItem;

/**
 * 管理員願望抽屜的資料（設計稿 16.3）：願望、機構卡、目前認領（含禮物價格與捐贈人姓名、帳號信箱）。
 *
 * <p>只有管理員看得到價格與捐贈人個資；有認領者時，取得這份資料會寫一筆「查看認領詳情」稽核。
 * 孩子的手寫信件照片在願望牆本來就公開，所以直接帶出、不寫稽核。寄件照片、對話、捐贈收據不在這裡——它們是點擊載入，各自走專用端點並各寫稽核。
 */
public record AdminWishDetailView(
        UUID id,
        String title,
        String childAlias,
        String ageRangeLabel,
        String avatar,
        String description,
        List<AttachmentView> letterPhotos,
        WishStatus status,
        Instant createdAt,
        String reviewNote,
        Frozen frozen,
        boolean late,
        OrganizationCard organization,
        ClaimCard claim,
        /** 曾上架過、修改後重新送審（審核中）；管理員重審時看這個旗標 */
        boolean resubmission,
        /** 與上次核准版本的差異；從沒有核准紀錄（功能上線前的舊願望）為 null */
        WishChanges changes,
        /** 這個願望的歷程時間軸 */
        List<WishHistoryItem> history) {

    public record Frozen(Instant at, String reason) {
    }

    public record OrganizationCard(
            UUID id, String name, String contactPerson, String mobilePhone, String contactEmail,
            String address) {

        public static OrganizationCard from(Organization organization) {
            return new OrganizationCard(organization.getId(), organization.getName(),
                    organization.getContactPerson(), organization.getMobilePhone(),
                    organization.getContactEmail(), organization.getAddress());
        }
    }

    public record ClaimCard(
            UUID claimId,
            ClaimStatus status,
            String donorDisplayName,
            String donorName,
            String donorEmail,
            Instant claimedAt,
            Instant shipDeadlineAt,
            Instant shippedAt,
            Instant receivedAt,
            LocalDate receivedOn,
            Instant completedAt,
            String trackingCarrier,
            String trackingNumber,
            String giftDescription,
            Integer giftQuantity,
            Integer giftPrice,
            LocalDate shippedOn,
            boolean receiptRequested) {

        public static ClaimCard from(Claim claim) {
            return new ClaimCard(
                    claim.getId(), claim.getStatus(),
                    claim.getDonor().getDisplayName(), claim.getDonor().getName(),
                    claim.getDonor().getEmail(),
                    claim.getClaimedAt(), claim.getShipDeadlineAt(), claim.getShippedAt(),
                    claim.getReceivedAt(), claim.getReceivedOn(), claim.getCompletedAt(),
                    claim.getTrackingCarrier(), claim.getTrackingNumber(),
                    claim.getGiftDescription(), claim.getGiftQuantity(), claim.getGiftPrice(),
                    claim.getShippedOn(), claim.isReceiptRequested());
        }
    }

    public static AdminWishDetailView from(Wish wish, Claim active, boolean late, List<AttachmentView> letterPhotos,
                                           WishChanges changes, List<WishHistoryItem> history) {
        return new AdminWishDetailView(
                wish.getId(), wish.getTitle(), wish.getChildAlias(),
                wish.getAgeRange() == null ? null : wish.getAgeRange().getLabel(),
                wish.getAvatar(), wish.getDescription(), letterPhotos, wish.getStatus(), wish.getCreatedAt(),
                wish.getReviewNote(),
                wish.isFrozen() ? new Frozen(wish.getFrozenAt(), wish.getFrozenReason()) : null,
                late,
                OrganizationCard.from(wish.getOrganization()),
                active == null ? null : ClaimCard.from(active),
                wish.getStatus() == WishStatus.PENDING_REVIEW && wish.getPublishedAt() != null,
                changes, history);
    }
}
