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
}
