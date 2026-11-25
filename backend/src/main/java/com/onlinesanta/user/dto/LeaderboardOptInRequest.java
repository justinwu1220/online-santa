package com.onlinesanta.user.dto;

import jakarta.validation.constraints.NotNull;

/** 排行榜「在排行榜顯示我的名字」開關。 */
public record LeaderboardOptInRequest(@NotNull(message = "請指定要開啟或關閉") Boolean optIn) {
}
