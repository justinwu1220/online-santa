-- 管理員檢視機構申請文件，新增稽核動作，CHECK 約束跟著放寬。

ALTER TABLE admin_audit_logs DROP CONSTRAINT ck_admin_audit_action;

ALTER TABLE admin_audit_logs ADD CONSTRAINT ck_admin_audit_action
    CHECK (action IN ('VIEW_CLAIM_DETAIL', 'VIEW_CLAIM_ATTACHMENTS', 'VIEW_CLAIM_MESSAGES',
                      'APPROVE_ORGANIZATION', 'REJECT_ORGANIZATION',
                      'SUSPEND_ORGANIZATION', 'REACTIVATE_ORGANIZATION',
                      'VIEW_ORGANIZATION_DOCUMENTS',
                      'DELETE_ATTACHMENT',
                      'RUN_RELEASE_SWEEP', 'RUN_ATTACHMENT_CLEANUP',
                      'RUN_DEADLINE_REMINDERS'));
