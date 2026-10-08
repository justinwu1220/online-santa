package com.onlinesanta.wish.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.onlinesanta.attachment.dto.AttachmentView;
import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishCategory;
import com.onlinesanta.wish.WishStatus;

/** 機構檢視自己願望時的視圖，多了操作狀態與稽核時間。 */
public record WishOrgView(
        UUID id,
        String title,
        String description,
        WishCategory category,
        AgeRange ageRange,
        PriceRange priceRange,
        String childAlias,
        String interests,
        WishStatus status,
        boolean editable,
        boolean deletable,
        long version,
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
        return new WishOrgView(
                wish.getId(),
                wish.getTitle(),
                wish.getDescription(),
                wish.getCategory(),
                wish.getAgeRange(),
                wish.getPriceRange(),
                wish.getChildAlias(),
                wish.getInterests(),
                wish.getStatus(),
                wish.getStatus().isEditable(),
                wish.isDeletable(),
                wish.getVersion(),
                wish.getPublishedAt(),
                wish.getCreatedAt(),
                wish.getUpdatedAt(),
                imageUrl,
                letterPhotos);
    }
}
