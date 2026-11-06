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

    /** 看了某機構的照片（可能拍到孩子或機構環境）。 */
    VIEW_ORGANIZATION_PHOTO,

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

    /** 看了某個願望的附件（孩子的手寫信件、寄件照片，可能含孩童影像與個資）。 */
    VIEW_WISH_ATTACHMENTS,

    /** 管理員停權認領者。 */
    SUSPEND_USER,

    /** 管理員解除認領者停權。 */
    REACTIVATE_USER,

    /** 管理員直接修改願望內容（個資外洩處置）。 */
    EDIT_WISH_CONTENT,

    /** 管理員釋出某筆認領（例如認領者違規）。 */
    RELEASE_CLAIM_BY_ADMIN,

    /** 管理員核准或退回機構的「申請解除」（凍結或停權）。 */
    DECIDE_UNLOCK_REQUEST,

    /** 管理員通知受影響的認領者（例如機構停權）。 */
    NOTIFY_AFFECTED_DONORS,

    /** 查看停權當下保全的原始資訊快照。 */
    VIEW_SUSPENSION_RECORD,

    /** 管理員遮蔽對話中的訊息（含個資的內容），原文保留給稽核。 */
    REDACT_MESSAGE,

    /** 管理員刪除願望的信件照片（外洩個資處置）。 */
    DELETE_WISH_LETTER,

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
                 SUSPEND_ORGANIZATION, REACTIVATE_ORGANIZATION, VIEW_ORGANIZATION_DOCUMENTS, VIEW_ORGANIZATION_PHOTO,
                 DELETE_ORGANIZATION_DOCUMENT, NOTIFY_AFFECTED_DONORS
                    -> AdminAuditTargetType.ORGANIZATION;
            case APPROVE_WISH, REJECT_WISH, FREEZE_WISH, UNFREEZE_WISH, VIEW_WISH_ATTACHMENTS, EDIT_WISH_CONTENT,
                 DECIDE_UNLOCK_REQUEST, DELETE_WISH_LETTER -> AdminAuditTargetType.WISH;
            case SUSPEND_USER, REACTIVATE_USER, VIEW_SUSPENSION_RECORD -> AdminAuditTargetType.USER;
            case RELEASE_CLAIM_BY_ADMIN, REDACT_MESSAGE -> AdminAuditTargetType.CLAIM;
            case RUN_RELEASE_SWEEP, RUN_ATTACHMENT_CLEANUP, RUN_DEADLINE_REMINDERS -> AdminAuditTargetType.SYSTEM;
        };
    }
}
