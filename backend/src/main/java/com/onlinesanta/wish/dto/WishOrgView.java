package com.onlinesanta.wish.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.onlinesanta.attachment.dto.AttachmentView;
import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimStatus;
import com.onlinesanta.wish.WishCategory;
import com.onlinesanta.wish.WishStage;
import com.onlinesanta.wish.WishStatus;

/** 機構檢視自己願望時的視圖，多了操作狀態與稽核時間。 */
public record WishOrgView(
        UUID id,
        String title,
        String description,
        WishCategory category,
        AgeRange ageRange,
        String ageRangeLabel,
        PriceRange priceRange,
        String childAlias,
        String interests,
        String avatar,
        WishStatus status,
        /** 管理員退回時的原因（退回後到重新核准前一直保留），沒有為 null */
        String reviewNote,
        boolean editable,
        boolean deletable,
        long version,
        /** 願望加上目前認領合起來的狀態（單一來源，前端不再自己推導） */
        WishStage stage,
        /** 目前佔用中的認領摘要；還沒有人認領為 null */
        ActiveClaimSummary activeClaim,
        /** 機構還沒讀的訊息數（系統通知＋認領者傳來的） */
        long unreadCount,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        String imageUrl,
        List<AttachmentView> letterPhotos) {

    public static WishOrgView from(Wish wish) {
        return from(wish, null, List.of());
    }

    /**
     * @param letterPhotos 孩子手寫的感謝卡／願望信照片，沒有就是空清單（不是 null）。
     *                      帶附件 id（而不只是網址），機構後台才能刪除自己上傳的照片
     */
    public static WishOrgView from(Wish wish, String imageUrl, List<AttachmentView> letterPhotos) {
        return from(wish, imageUrl, letterPhotos, null, 0, Instant.now());
    }

    public static WishOrgView from(Wish wish, String imageUrl, List<AttachmentView> letterPhotos,
                                   Claim activeClaim, long unreadCount, Instant now) {
        return new WishOrgView(
                wish.getId(),
                wish.getTitle(),
                wish.getDescription(),
                wish.getCategory(),
                wish.getAgeRange(),
                wish.getAgeRange() == null ? null : wish.getAgeRange().getLabel(),
                wish.getPriceRange(),
                wish.getChildAlias(),
                wish.getInterests(),
                wish.getAvatar(),
                wish.getStatus(),
                wish.getReviewNote(),
                wish.getStatus().isEditable(),
                wish.isDeletable(),
                wish.getVersion(),
                stageOf(wish, activeClaim, now),
                activeClaim == null ? null : ActiveClaimSummary.from(activeClaim, now),
                unreadCount,
                wish.getPublishedAt(),
                wish.getCreatedAt(),
                wish.getUpdatedAt(),
                imageUrl,
                letterPhotos);
    }

    /** 願望狀態＋目前認領 → 機構後台看到的合併狀態。逾期沿用 {@link Claim#isOverdue} 的同一個條件。 */
    public static WishStage stageOf(Wish wish, Claim activeClaim, Instant now) {
        return switch (wish.getStatus()) {
            case DRAFT -> WishStage.DRAFT;
            case PENDING_REVIEW -> WishStage.PENDING_REVIEW;
            case AVAILABLE -> WishStage.OPEN;
            case FULFILLED -> WishStage.DONE;
            case CLAIMED -> {
                if (activeClaim == null) {
                    yield WishStage.INCONSISTENT;
                }
                ClaimStatus status = activeClaim.getStatus();
                if (status == ClaimStatus.CLAIMED) {
                    yield activeClaim.isOverdue(now) ? WishStage.CLAIMED_OVERDUE : WishStage.CLAIMED_WAITING;
                }
                // RECEIVED 是舊流程留下的「已收到、待完成」，機構要做的事跟已寄出一樣
                yield status == ClaimStatus.SHIPPED || status == ClaimStatus.RECEIVED
                        ? WishStage.SHIPPED : WishStage.INCONSISTENT;
            }
        };
    }
}
