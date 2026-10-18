package com.onlinesanta.wish.dto;

import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.WishCategory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 建立與更新願望的請求內容（兩者欄位相同，故共用）。
 *
 * <p>刻意沒有孩童姓名或照片欄位。上限長度與資料庫欄位一致，讓錯誤在驗證階段就被攔下，
 * 而不是等到資料庫丟出截斷錯誤。
 */
public record WishRequest(
        @NotBlank(message = "請填寫孩子的暱稱")
        @Size(max = 50, message = "暱稱不可超過 50 字")
        String childAlias,

        @NotNull(message = "請選擇年齡區間")
        AgeRange ageRange,

        @Size(max = 500, message = "興趣描述不可超過 500 字")
        String interests,

        @NotBlank(message = "請填寫願望標題")
        @Size(max = 120, message = "標題不可超過 120 字")
        String title,

        /** 願望說明：孩子口吻的一句話，20 字內（舊願望的長描述照常顯示，只有新增與編輯時限制）。 */
        @Size(max = 20, message = "願望說明不可超過 20 字")
        String description,

        /** 1018 新版起機構表單不再填禮物分類；舊的呼叫端仍可帶，省略也可以。 */
        WishCategory category,

        /** 機構後台的「新增願望」已拿掉這個欄位——選填，省略時願望牆不顯示價格標籤。 */
        PriceRange priceRange) {

}
