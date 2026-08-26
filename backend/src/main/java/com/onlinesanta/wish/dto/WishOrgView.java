package com.onlinesanta.wish.dto;

import java.time.Instant;
import java.util.UUID;

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
        Instant updatedAt) {

    public static WishOrgView from(Wish wish) {
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
                wish.getUpdatedAt());
    }
}
