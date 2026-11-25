package com.onlinesanta.leaderboard;

import java.util.UUID;

import com.onlinesanta.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 每一屆活動的前三名（榮譽榜）。
 *
 * <p>稱號是累計的成就、解鎖後永久持有，所以不需要逐人的歷年快照；但「某一屆的前三名是誰」
 * 是那一屆的結果，必須定住——日後認領資料被整理、或有人在下一屆超車，都不該改寫歷史。
 *
 * <p>並列時同名次，所以一屆可能有三筆以上（例如兩個第 1 名、一個第 3 名）；
 * （檔期、使用者）唯一，重跑結案只會更新同一列。
 *
 * <p>不存名字：顯示名稱隨時可改，而且公開與否由本人決定，要顯示時再讀目前的值。
 */
@Entity
@Table(name = "campaign_podiums")
public class CampaignPodium extends BaseEntity {

    @Column(name = "campaign_id", nullable = false, updatable = false)
    private UUID campaignId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** 名次 1～3（欄位名避開 SQL 的 rank 視窗函式） */
    @Column(name = "rank_no", nullable = false)
    private int rank;

    /** 該屆的送達份數 */
    @Column(nullable = false)
    private int delivered;

    protected CampaignPodium() {
    }

    public CampaignPodium(UUID campaignId, UUID userId, int rank, int delivered) {
        this.campaignId = campaignId;
        this.userId = userId;
        this.rank = rank;
        this.delivered = delivered;
    }

    /** 重跑結案時更新同一列。 */
    public void update(int rank, int delivered) {
        this.rank = rank;
        this.delivered = delivered;
    }

    public UUID getCampaignId() {
        return campaignId;
    }

    public UUID getUserId() {
        return userId;
    }

    public int getRank() {
        return rank;
    }

    public int getDelivered() {
        return delivered;
    }
}
