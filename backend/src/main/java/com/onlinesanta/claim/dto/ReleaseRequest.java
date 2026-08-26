package com.onlinesanta.claim.dto;

import jakarta.validation.constraints.Size;

/** 釋回或取消認領時的原因說明，會寫入稽核軌跡。 */
public record ReleaseRequest(
        @Size(max = 255, message = "原因不可超過 255 字")
        String reason) {

    public static ReleaseRequest empty() {
        return new ReleaseRequest(null);
    }
}
