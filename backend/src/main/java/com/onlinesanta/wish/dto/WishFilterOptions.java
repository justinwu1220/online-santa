package com.onlinesanta.wish.dto;

import java.util.Arrays;
import java.util.List;

import com.onlinesanta.organization.Organization;
import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.WishCategory;

/**
 * 願望牆的篩選選項。
 *
 * <p>分類、年齡由後端提供而非前端寫死，避免兩邊的 enum 值不同步——新增一個分類時
 * 只要改後端，前端的篩選器會自動出現新選項。價格區間原本也在這裡（給機構後台
 * 建立/編輯願望的價格選單用），機構後台拿掉「預估價格」欄位後已經沒有呼叫端在用，
 * 整欄拿掉——`priceRange` 欄位本身在 `Wish`/DTO 裡仍存在（選填），只是不再需要
 * 一份下拉選單選項。
 *
 * <p>{@code organizations} 跟上面兩個不同，不是 enum——來自資料庫的已核准機構清單，
 * 由呼叫端查好傳進來，{@code build()} 本身不碰資料庫。
 */
public record WishFilterOptions(
        List<Option> categories,
        List<Option> ageRanges,
        List<Option> organizations) {

    public record Option(String value, String label) {
    }

    public static WishFilterOptions build(List<Organization> approvedOrganizations) {
        return new WishFilterOptions(
                Arrays.stream(WishCategory.values())
                        .map(c -> new Option(c.name(), c.getLabel())).toList(),
                Arrays.stream(AgeRange.values())
                        .map(a -> new Option(a.name(), a.getLabel())).toList(),
                approvedOrganizations.stream()
                        .map(o -> new Option(o.getId().toString(), o.getName())).toList());
    }
}
