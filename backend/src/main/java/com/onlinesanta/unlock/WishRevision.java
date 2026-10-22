package com.onlinesanta.unlock;

import com.onlinesanta.wish.AgeRange;

import jakarta.validation.constraints.Size;

/**
 * 願望凍結期間，機構修改後的內容（只有可能含個資的欄位）。<strong>沒有禮物（願望標題）</strong>：
 * 認領者可能已經照禮物買好了，認領後禮物鎖定。欄位為 null 代表不修改。
 */
public record WishRevision(
        @Size(max = 50, message = "暱稱不可超過 50 字")
        String childAlias,
        AgeRange ageRange,
        @Size(max = 500, message = "興趣描述不可超過 500 字")
        String interests,
        @Size(max = 20, message = "願望說明不可超過 20 字")
        String description) {

    public boolean isEmpty() {
        return childAlias == null && ageRange == null && interests == null && description == null;
    }
}
