package com.onlinesanta.wish.dto;

import java.time.Instant;
import java.util.UUID;

import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishCategory;
import com.onlinesanta.wish.WishStatus;

/**
 * 願望牆清單的單筆項目。
 *
 * <p>與 {@link WishPublicView}（願望詳情）分開成兩個型別，而不是共用一個 DTO 再視
 * 情況把 imageUrl 填 null——清單本來就不顯示圖片（一律用分類圖示，見體檢後的流量
 * 優化決策），型別上乾脆不給這個欄位存在的空間，「清單端點不可能回傳圖片網址」
 * 因此是編譯期就成立的事實，也讓 {@link com.onlinesanta.wish.WishController#browse}
 * 不必再為了組這個回應去查一次示意圖網址——那是願望牆的熱門路徑，省下的不只是
 * 回應裡的 egress，還有每頁一次的批次查詢。
 */
public record WishListView(
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
        String organizationName) {

    public static WishListView from(Wish wish) {
        return new WishListView(
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
                wish.getOrganization().getName());
    }
}
