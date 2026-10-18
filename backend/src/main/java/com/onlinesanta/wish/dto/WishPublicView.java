package com.onlinesanta.wish.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishCategory;
import com.onlinesanta.wish.WishStatus;

/**
 * 一般民眾看到的願望。
 *
 * <p>與 {@link WishOrgView} 分開成兩個型別，而不是共用一個 DTO 再視情況清空欄位——
 * 後者只要有人漏了一次判斷就會外洩。型別分離讓「公開端點不可能回傳內部欄位」
 * 成為編譯期就成立的事實。
 */
public record WishPublicView(
        UUID id,
        String title,
        String description,
        WishCategory category,
        String categoryLabel,
        AgeRange ageRange,
        String ageRangeLabel,
        PriceRange priceRange,
        String priceRangeLabel,
        String childAlias,
        String interests,
        WishStatus status,
        Instant publishedAt,
        UUID organizationId,
        String organizationName,
        /** 機構在「機構設定」填的簡介，願望頁的「這份願望來自」區塊顯示；沒填為 null */
        String organizationDescription,
        String imageUrl,
        List<String> letterPhotoUrls,
        int shipDeadlineDays) {

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    public static WishPublicView from(Wish wish) {
        return from(wish, null, List.of(), 0);
    }

    /**
     * @param imageUrl        禮物示意圖網址，沒有圖時為 null
     * @param letterPhotoUrls 孩子手寫的感謝卡／願望信照片，沒有就是空清單（不是 null）
     * @param shipDeadlineDays 認領後預計要在幾天內寄出（依機構的逾期處理設定），供認領前預告日期
     */
    public static WishPublicView from(
            Wish wish, String imageUrl, List<String> letterPhotoUrls, int shipDeadlineDays) {
        return new WishPublicView(
                wish.getId(),
                wish.getTitle(),
                wish.getDescription(),
                wish.getCategory(),
                wish.getCategory().getLabel(),
                wish.getAgeRange(),
                wish.getAgeRange().getLabel(),
                wish.getPriceRange(),
                wish.getPriceRange() != null ? wish.getPriceRange().getLabel() : null,
                wish.getChildAlias(),
                wish.getInterests(),
                wish.getStatus(),
                wish.getPublishedAt(),
                wish.getOrganization().getId(),
                wish.getOrganization().getName(),
                blankToNull(wish.getOrganization().getDescription()),
                imageUrl,
                letterPhotoUrls,
                shipDeadlineDays);
    }
}
