package com.onlinesanta.claim.dto;

import jakarta.validation.constraints.Size;

/**
 * 認領時可附上給孩子的話。
 *
 * <p>整個請求本體都是選填，因此 body 可以省略；controller 會補上空的預設值。
 */
public record ClaimRequest(
        @Size(max = 500, message = "給孩子的話不可超過 500 字")
        String donorMessage) {

    public static ClaimRequest empty() {
        return new ClaimRequest(null);
    }
}
