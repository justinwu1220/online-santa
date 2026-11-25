package com.onlinesanta.leaderboard.dto;

import com.onlinesanta.leaderboard.Tier;

/**
 * 排行榜頁右欄「我的成就」（SPEC 第 3.6 節）。只有本人看得到。
 *
 * <p>名次與份數是<strong>當期</strong>的，而且是即時算的——機構按下「確認收到並完成」之後
 * 要立刻反映；稱號則看<strong>累計</strong>份數，解鎖之後永久持有，不隨檔期重置。
 *
 * @param ranked             這一屆是否已經上榜（當期送達至少 1 份）。false 時 rank 為 null
 * @param delivered          當期送達份數
 * @param rank               當期名次
 * @param lifetimeDelivered  歷年累計送達份數（稱號看這個）
 * @param tier               目前持有的稱號；一份都還沒送達過時為 null
 * @param nextTier           下一個稱號；已經是最高一級時為 null
 * @param toNextTier         還差幾份解鎖下一個稱號；沒有下一級時為 0
 * @param showName           是否在排行榜公開顯示名字（預設 false）
 */
public record MyAchievementView(
        boolean ranked,
        int delivered,
        Integer rank,
        int lifetimeDelivered,
        Tier tier,
        Tier nextTier,
        int toNextTier,
        boolean showName) {
}
