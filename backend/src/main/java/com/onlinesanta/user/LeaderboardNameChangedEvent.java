package com.onlinesanta.user;

import java.util.UUID;

/**
 * 這個人在排行榜上顯示的名字變了：切換了「在排行榜顯示我的名字」，或改了顯示名稱。
 *
 * <p>排行榜名單是整份一起算、整份一起快取的（見 {@code LeaderboardService}），
 * 名字換了就得把那份丟掉重算，否則最多五分鐘榜上還是舊的，看起來像開關壞了。
 * 交易提交後才送出，監聽端重算時才看得到新值。
 */
public record LeaderboardNameChangedEvent(UUID userId) {
}
