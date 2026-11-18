package com.onlinesanta.notification;

/**
 * 通知信樣板清單。對應 {@code resources/mail-templates/} 底下的樣板檔，檔名是常數名稱小寫加連字號
 * （{@code CLAIM_CONFIRMED} → {@code claim-confirmed.html}）。設計端更新樣板時，
 * 對照 {@link #sourceName()} 找到那一份，整檔覆蓋即可。
 */
public enum MailTemplateId {

    // ── 既有通知（編號對照 Resend 自動寄信內容 README）
    CLAIM_CONFIRMED("1.2_認領成立_認領者確認信"),
    DEADLINE_REMINDER("1.3_寄送期限提醒"),
    CLAIM_RECALLED("1.4_機構收回認領"),
    FEEDBACK_PHOTO("1.5_機構已上傳收件證明"),
    ORG_APPROVED("1.7_機構申請審核通過"),
    ORG_NEEDS_FIX("1.7_機構申請需要補件"),
    WISH_PAUSED("1.9_願望暫停寄送"),
    WISH_RESUMED("1.9_願望恢復寄送"),
    ORG_SUSPENDED("1.10_機構暫時停權"),
    ORG_REINSTATED("1.10_機構解除停權"),
    ORG_SUSPENDED_CLAIM_PAUSED("1.11_機構停權_給認領者的暫停寄送通知"),
    ORG_REINSTATED_CLAIM_RESUMED("1.11_機構復權_給認領者的恢復寄送通知"),
    TEAM_CASE_NOTICE("1.12_團隊給認領者的個案通知"),
    USER_SUSPENDED("1.13_認領者帳號暫時停權"),
    USER_REINSTATED("1.13_認領者帳號已恢復"),
    CLAIM_RELEASED_BY_ADMIN("1.14_管理員結束認領"),

    // ── 新增通知
    WISH_FROZEN_BY_ADMIN("4.1_願望已凍結"),
    WISH_EDITED_BY_ADMIN("4.1_團隊已修改願望內容"),
    WISH_PHOTO_REMOVED_BY_ADMIN("4.1_團隊已移除願望卡照片"),
    WISH_UNFROZEN_BY_ADMIN("4.1_管理員已解除願望凍結"),
    CLAIM_AUTO_RELEASED("4.2_逾期認領已自動收回"),
    CLAIM_OVERDUE_MANUAL("4.4_逾期未回報寄送_人工處理"),
    CLAIM_OVERDUE_AUTO("4.4_逾期未回報寄送_自動收回"),
    WISH_UNLOCK_APPROVED("4.5_願望解除凍結申請通過"),
    WISH_UNLOCK_NEEDS_INFO("4.5_願望解除凍結申請需補充"),
    ADMIN_DIGEST("4.8_管理員每日待審摘要"),
    ORG_UNLOCK_REJECTED("4.9_機構解除停權申請需補充"),
    ORG_APPLICATION_RECEIVED("4.10_已收到機構參與申請"),
    ORG_RESUBMISSION_RECEIVED("4.10_已收到機構重新送審資料");

    private final String sourceName;

    MailTemplateId(String sourceName) {
        this.sourceName = sourceName;
    }

    /** 設計端交付時的原檔名（僅供對照；放進專案時改成 ASCII 檔名）。 */
    public String sourceName() {
        return sourceName;
    }

    /** classpath 上的資源路徑。檔名用 ASCII：中文檔名在 POSIX locale 的 Linux 上可能讀不到。 */
    public String resourcePath() {
        return "mail-templates/" + name().toLowerCase().replace('_', '-') + ".html";
    }
}
