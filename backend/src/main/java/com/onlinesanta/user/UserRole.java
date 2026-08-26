package com.onlinesanta.user;

/** 平台角色。對應 users.role 的 CHECK 約束。 */
public enum UserRole {
    /** 一般民眾（買家）：瀏覽願望、認領、寄送。 */
    DONOR,
    /** 機構成員（賣家）：上架願望、管理認領、回傳送禮回饋。 */
    ORG_MEMBER,
    /** 平台管理員：審核機構註冊。 */
    ADMIN
}
