-- 管理員檢視認領對話改成跟查看附件一樣，需要獨立稽核動作，CHECK 約束跟著放寬。

ALTER TABLE admin_audit_logs DROP CONSTRAINT ck_admin_audit_action;

ALTER TABLE admin_audit_logs ADD CONSTRAINT ck_admin_audit_action
    CHECK (action IN ('VIEW_CLAIM_DETAIL', 'VIEW_CLAIM_ATTACHMENTS', 'VIEW_CLAIM_MESSAGES',
                      'APPROVE_ORGANIZATION', 'REJECT_ORGANIZATION',
                      'SUSPEND_ORGANIZATION', 'REACTIVATE_ORGANIZATION',
                      'DELETE_ATTACHMENT',
                      'RUN_RELEASE_SWEEP', 'RUN_ATTACHMENT_CLEANUP',
                      'RUN_DEADLINE_REMINDERS'));
