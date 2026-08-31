package com.onlinesanta.organization;

/** 機構的審核狀態。對應 organizations.status 的 CHECK 約束。 */
public enum OrganizationStatus {
    /** 已自助註冊，等待平台管理員審核。 */
    PENDING,
    /** 審核通過，可以上架願望。 */
    APPROVED,
    /** 審核未通過；機構可補件後重新送審。 */
    REJECTED,
    /** 曾通過但遭停權。 */
    SUSPENDED;

    public boolean canPublishWishes() {
        return this == APPROVED;
    }

    /**
     * 能否建立與保有草稿。
     *
     * <p>比 {@link #canPublishWishes()} 寬鬆：審核期間機構就能先把願望的文字準備好，
     * 核准後一鍵上架。否則審核對機構而言是一段什麼都不能做的空白，而後台的文案
     * （「現在可以先把願望存成草稿」）也會與實際行為矛盾。
     *
     * <p>草稿不會出現在願望牆上，所以放寬這一層沒有隱私上的代價——真正的把關是
     * 上架，那仍然要求 APPROVED。
     *
     * <p>停權是唯一的硬停止：那是曾經通過、後來被撤銷資格的機構，不該再累積內容。
     */
    public boolean canDraftWishes() {
        return this != SUSPENDED;
    }
}
