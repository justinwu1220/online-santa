package com.onlinesanta.admin.dto;

import jakarta.validation.constraints.Size;

/** 核准機構的解除凍結申請：附註選填；信件目前被屏蔽時可選擇是否恢復顯示。 */
public record ApproveUnlockRequest(
        @Size(max = 1000, message = "附註不可超過 1000 字")
        String note,
        Boolean restoreLetter) {

    public boolean shouldRestoreLetter() {
        return Boolean.TRUE.equals(restoreLetter);
    }
}
