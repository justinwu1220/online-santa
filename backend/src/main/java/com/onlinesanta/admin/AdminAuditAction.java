package com.onlinesanta.admin;

/**
 * 需要留下紀錄的管理員動作。對應 admin_audit_logs.action 的 CHECK 約束。
 *
 * <p>只記錄「針對特定個人的存取」與「改變狀態的決定」。清單頁不記——它只顯示彙總與
 * 狀態，量又大，會把真正重要的紀錄淹沒。
 */
public enum AdminAuditAction {

    /** 打開了某一筆認領的詳情，看到捐贈者的姓名與聯絡方式。 */
    VIEW_CLAIM_DETAIL,

    /** 看了某一筆認領的附件——可能包含寄送證明與含孩童影像的回饋照片。 */
    VIEW_CLAIM_ATTACHMENTS,

    APPROVE_ORGANIZATION,
    REJECT_ORGANIZATION,

    /** 停權：資安事件應變手冊的第一步，見 PRIVACY.md。 */
    SUSPEND_ORGANIZATION,
    /** 復權：停權後恢復為 APPROVED。 */
    REACTIVATE_ORGANIZATION,

    /** 管理員刪除附件（隱私事件處置）。目標是附件所屬的認領。 */
    DELETE_ATTACHMENT,

    /** 手動觸發逾期釋回掃描。 */
    RUN_RELEASE_SWEEP,

    /** 手動觸發 PENDING 附件清理排程。 */
    RUN_ATTACHMENT_CLEANUP;

    public AdminAuditTargetType targetType() {
        return switch (this) {
            case VIEW_CLAIM_DETAIL, VIEW_CLAIM_ATTACHMENTS, DELETE_ATTACHMENT -> AdminAuditTargetType.CLAIM;
            case APPROVE_ORGANIZATION, REJECT_ORGANIZATION,
                 SUSPEND_ORGANIZATION, REACTIVATE_ORGANIZATION -> AdminAuditTargetType.ORGANIZATION;
            case RUN_RELEASE_SWEEP, RUN_ATTACHMENT_CLEANUP -> AdminAuditTargetType.SYSTEM;
        };
    }
}
