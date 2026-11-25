package com.onlinesanta.leaderboard.dto;

import java.util.List;

import com.onlinesanta.leaderboard.Tier;

/**
 * 公開的排行榜（SPEC 第 8 節）。
 *
 * <p><strong>這份資料任何人都看得到</strong>，所以只有名次、名稱、份數與稱號：
 * 沒有使用者 id、信箱、真實姓名，也沒有禮物內容、價格、願望或機構資訊。
 *
 * <p>刻意<strong>不標「這一列是我」</strong>：標了就人人不同，CDN 擋不住，每個訪客都會叫醒
 * 一次 Cloud Run。前端改用「我的成就」回來的名次與份數去highlight自己那一列
 * （並列時可能標到同名次的另一列，但兩列顯示的內容一模一樣，看不出差別）。
 *
 * @param goal        目標份數＝本檔期上架的願望總數
 * @param delivered   全站已送達份數
 * @param donorCount  上榜人數（送達至少 1 份的認領者）
 */
public record LeaderboardView(String campaignName, int goal, int delivered, int donorCount, List<Entry> entries) {

    /**
     * @param anonymous 這一列沒有公開名字，{@code name} 是「匿名的聖誕老人」
     */
    public record Entry(int rank, String name, int delivered, Tier tier, boolean anonymous) {
    }
}
