package com.onlinesanta.leaderboard.dto;

import java.util.List;

/**
 * 歷屆前三名（榮譽榜）。公開資料，與排行榜名單同一套規則：沒有識別資料，
 * 沒有自己開啟公開顯示名字的人一律是「匿名的聖誕老人」。
 */
public record PodiumHistoryView(List<CampaignPodiumView> campaigns) {

    /** 一屆活動的前三名；並列時同名次，所以可能超過三筆。 */
    public record CampaignPodiumView(String campaignName, List<Entry> entries) {

        public record Entry(int rank, String name, int delivered, boolean anonymous) {
        }
    }
}
