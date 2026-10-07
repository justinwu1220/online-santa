package com.onlinesanta.wish.dto;

import java.util.Arrays;
import java.util.List;

import com.onlinesanta.organization.Organization;
import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.WishCategory;

/**
 * 願望牆的篩選選項。
 *
 * <p>分類、年齡、價格由後端提供而非前端寫死，避免兩邊的 enum 值不同步——新增一個
 * 分類時只要改後端，前端的篩選器會自動出現新選項。{@code priceRanges} 願望牆本身
 * 已不使用（預算篩選已移除），保留是因為機構後台建立/編輯願望的價格選單還在用它。
 *
 * <p>{@code organizations} 跟上面三個不同，不是 enum——來自資料庫的已核准機構清單，
 * 由呼叫端查好傳進來，{@code build()} 本身不碰資料庫。
 */
public record WishFilterOptions(
        List<Option> categories,
        List<Option> ageRanges,
        List<Option> priceRanges,
        List<Option> organizations) {

    public record Option(String value, String label) {
    }

    public static WishFilterOptions build(List<Organization> approvedOrganizations) {
        return new WishFilterOptions(
                Arrays.stream(WishCategory.values())
                        .map(c -> new Option(c.name(), c.getLabel())).toList(),
                Arrays.stream(AgeRange.values())
                        .map(a -> new Option(a.name(), a.getLabel())).toList(),
                Arrays.stream(PriceRange.values())
                        .map(p -> new Option(p.name(), p.getLabel())).toList(),
                approvedOrganizations.stream()
                        .map(o -> new Option(o.getId().toString(), o.getName())).toList());
    }
}
