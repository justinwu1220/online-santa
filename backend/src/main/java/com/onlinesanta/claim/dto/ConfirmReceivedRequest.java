package com.onlinesanta.claim.dto;

import java.time.LocalDate;

import com.onlinesanta.common.NotFutureInTaiwan;

import jakarta.validation.constraints.AssertTrue;

/**
 * 機構「確認收到並完成」。
 *
 * @param receivedOn 實際收到日期；沒填視為台灣今天。不可是未來，也不可早於認領者回報的寄出日期
 * @param inspected  「已拆封檢查，沒有禁止物品或可疑裝置」，一定要勾
 */
public record ConfirmReceivedRequest(
        @NotFutureInTaiwan(message = "實際收到日期不可是未來的日期")
        LocalDate receivedOn,

        @AssertTrue(message = "請確認已拆封檢查，沒有禁止物品或可疑裝置")
        boolean inspected) {
}
