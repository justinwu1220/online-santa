package com.onlinesanta.admin.dto;

import com.onlinesanta.wish.AgeRange;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理員直接修改願望內容（個資外洩處置）。欄位為 null 代表不修改；<strong>沒有禮物（標題）欄位</strong>：
 * 已認領的願望，認領者可能已經照禮物買好了。
 *
 * @param freeze 是否同時凍結（預設是）：凍結後機構看得到原因、可修改後申請解除，認領者看到「暫停處理中」
 */
public record WishContentEditRequest(
        @Size(max = 50, message = "暱稱不可超過 50 字")
        String childAlias,
        AgeRange ageRange,
        @Size(max = 500, message = "興趣描述不可超過 500 字")
        String interests,
        @Size(max = 50, message = "願望說明不可超過 50 字")
        String description,
        @NotBlank(message = "請填寫處置原因")
        @Size(min = 4, max = 500, message = "處置原因至少 4 個字，最多 500 字")
        String reason,
        Boolean freeze) {

    public boolean shouldFreeze() {
        return freeze == null || freeze;
    }
}
