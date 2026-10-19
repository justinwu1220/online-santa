package com.onlinesanta.claim.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 機構收回認領（還沒寄出的才能收回）。原因四選一，選「其他」可以補寫說明。
 */
public record RecallRequest(
        @NotNull(message = "請選擇收回原因")
        Reason reason,

        @Size(max = 200, message = "補充說明不可超過 200 字")
        String detail) {

    public enum Reason {
        OVERDUE("逾期未寄送"),
        UNREACHABLE("認領者聯繫不上"),
        CANNOT_COMPLETE("認領者表示無法完成"),
        /** 機構要修改願望內容：收回後願望會下架修改、重新審核才再上架。 */
        EDIT_WISH("需要修改願望內容"),
        OTHER("其他");

        private final String label;

        Reason(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /** 寫進認領紀錄的原因文字，例如「其他：孩子已轉到別的機構」。 */
    public String text() {
        String extra = detail == null ? "" : detail.strip();
        return reason == Reason.OTHER && !extra.isEmpty()
                ? reason.getLabel() + "：" + extra
                : reason.getLabel();
    }
}
