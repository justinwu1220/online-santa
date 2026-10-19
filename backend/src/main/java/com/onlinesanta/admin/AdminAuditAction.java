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

    /** 看了某一筆認領的對話內容——捐贈者與機構之間可能提到的收件細節等資訊。 */
    VIEW_CLAIM_MESSAGES,

    /** 看了某一筆認領的捐贈收據資料——含身分證字號／統一編號與發票證明。 */
    VIEW_CLAIM_RECEIPT,

    APPROVE_ORGANIZATION,
    REJECT_ORGANIZATION,

    /** 停權：資安事件應變手冊的第一步，見 PRIVACY.md。 */
    SUSPEND_ORGANIZATION,
    /** 復權：停權後恢復為 APPROVED。 */
    REACTIVATE_ORGANIZATION,

    /** 看了某機構申請附上的立案證明文件。 */
    VIEW_ORGANIZATION_DOCUMENTS,

    /** 管理員刪除附件（隱私事件處置）。目標是附件所屬的認領。 */
    DELETE_ATTACHMENT,

    /** 管理員刪除機構文件——跟 DELETE_ATTACHMENT 分開記，因為目標是機構不是認領。 */
    DELETE_ORGANIZATION_DOCUMENT,

    /** 核准機構提交的願望，立即公開在願望牆。 */
    APPROVE_WISH,

    /** 退回機構提交的願望，附原因。 */
    REJECT_WISH,

    /** 暫時凍結願望：從願望牆與認領者頁面消失。 */
    FREEZE_WISH,

    /** 解除凍結。 */
    UNFREEZE_WISH,

    /** 手動觸發逾期釋回掃描。 */
    RUN_RELEASE_SWEEP,

    /** 手動觸發 PENDING 附件清理排程。 */
    RUN_ATTACHMENT_CLEANUP,

    /** 手動觸發寄送期限提醒排程。 */
    RUN_DEADLINE_REMINDERS;

    public AdminAuditTargetType targetType() {
        return switch (this) {
            case VIEW_CLAIM_DETAIL, VIEW_CLAIM_ATTACHMENTS, VIEW_CLAIM_MESSAGES, VIEW_CLAIM_RECEIPT,
                 DELETE_ATTACHMENT
                    -> AdminAuditTargetType.CLAIM;
            case APPROVE_ORGANIZATION, REJECT_ORGANIZATION,
                 SUSPEND_ORGANIZATION, REACTIVATE_ORGANIZATION, VIEW_ORGANIZATION_DOCUMENTS,
                 DELETE_ORGANIZATION_DOCUMENT
                    -> AdminAuditTargetType.ORGANIZATION;
            case APPROVE_WISH, REJECT_WISH, FREEZE_WISH, UNFREEZE_WISH -> AdminAuditTargetType.WISH;
            case RUN_RELEASE_SWEEP, RUN_ATTACHMENT_CLEANUP, RUN_DEADLINE_REMINDERS -> AdminAuditTargetType.SYSTEM;
        };
    }
}
