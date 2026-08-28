package com.onlinesanta.admin.dto;

import java.time.Instant;
import java.util.UUID;

import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishCategory;
import com.onlinesanta.wish.WishStatus;

/**
 * 管理員的跨機構願望檢視。
 *
 * <p>與 {@code WishOrgView} 分開：機構看的是自己的願望，管理員看的是「哪個機構的
 * 哪個願望」，因此多了機構欄位。型別分開才不會有人為了省事把機構欄位加進機構自己
 * 的視圖裡。
 */
public record AdminWishView(
        UUID id,
        String title,
        String childAlias,
        AgeRange ageRange,
        WishCategory category,
        PriceRange priceRange,
        WishStatus status,
        UUID organizationId,
        String organizationName,
        Instant publishedAt,
        Instant createdAt) {

    public static AdminWishView from(Wish wish) {
        return new AdminWishView(
                wish.getId(),
                wish.getTitle(),
                wish.getChildAlias(),
                wish.getAgeRange(),
                wish.getCategory(),
                wish.getPriceRange(),
                wish.getStatus(),
                wish.getOrganization().getId(),
                wish.getOrganization().getName(),
                wish.getPublishedAt(),
                wish.getCreatedAt());
    }
}
