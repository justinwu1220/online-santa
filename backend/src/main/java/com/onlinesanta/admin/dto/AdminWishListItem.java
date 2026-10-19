package com.onlinesanta.admin.dto;

import java.util.UUID;

import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishStatus;

/** 管理員「願望」清單的一列：狀態、標題、機構、孩子，加上「逾期未寄」「已凍結」徽章。 */
public record AdminWishListItem(
        UUID id,
        String title,
        String childAlias,
        String ageRangeLabel,
        String avatar,
        WishStatus status,
        boolean frozen,
        boolean late,
        UUID organizationId,
        String organizationName) {

    public static AdminWishListItem from(Wish wish, boolean late) {
        return new AdminWishListItem(
                wish.getId(), wish.getTitle(), wish.getChildAlias(),
                wish.getAgeRange() == null ? null : wish.getAgeRange().getLabel(),
                wish.getAvatar(), wish.getStatus(), wish.isFrozen(), late,
                wish.getOrganization().getId(), wish.getOrganization().getName());
    }
}
