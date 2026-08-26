package com.onlinesanta.wish.dto;

import java.util.Arrays;
import java.util.List;

import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.WishCategory;

/**
 * 願望牆的篩選選項。
 *
 * <p>由後端提供而非前端寫死，避免兩邊的 enum 值不同步——新增一個分類時只要改後端，
 * 前端的篩選器會自動出現新選項。
 */
public record WishFilterOptions(
        List<Option> categories,
        List<Option> ageRanges,
        List<Option> priceRanges) {

    public record Option(String value, String label) {
    }

    public static WishFilterOptions build() {
        return new WishFilterOptions(
                Arrays.stream(WishCategory.values())
                        .map(c -> new Option(c.name(), c.getLabel())).toList(),
                Arrays.stream(AgeRange.values())
                        .map(a -> new Option(a.name(), a.getLabel())).toList(),
                Arrays.stream(PriceRange.values())
                        .map(p -> new Option(p.name(), p.getLabel())).toList());
    }
}
