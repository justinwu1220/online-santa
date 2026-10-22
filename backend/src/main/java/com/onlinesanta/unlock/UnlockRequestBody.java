package com.onlinesanta.unlock;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** 機構申請解除凍結的請求內容。revision 為 null 或空，代表沒有修改內容、只是說明並申請。 */
public record UnlockRequestBody(
        @Size(max = 1000, message = "說明不可超過 1000 字")
        String message,
        @Valid
        WishRevision revision) {
}
