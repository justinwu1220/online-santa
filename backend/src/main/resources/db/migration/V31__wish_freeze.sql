-- 1018 新版：管理員暫時凍結願望（設計稿 16.3）。
--
-- 凍結不是狀態，而是疊在任何公開狀態上的旗標：願望原本是可認領、已認領或已完成都可以凍結，
-- 解除後回到原本的狀態。凍結期間：願望牆、詳情頁、認領者的「我的認領」都看不到，也不能被認領；
-- 機構後台與管理員後台仍保留。

ALTER TABLE wishes ADD COLUMN frozen_at timestamptz;
ALTER TABLE wishes ADD COLUMN frozen_reason text;
ALTER TABLE wishes ADD COLUMN frozen_by uuid;

ALTER TABLE wishes ADD CONSTRAINT ck_wishes_frozen
    CHECK ((frozen_at IS NULL AND frozen_reason IS NULL) OR (frozen_at IS NOT NULL AND frozen_reason IS NOT NULL));

CREATE INDEX idx_wishes_frozen ON wishes (frozen_at) WHERE frozen_at IS NOT NULL;

ALTER TABLE admin_audit_logs DROP CONSTRAINT ck_admin_audit_action;
ALTER TABLE admin_audit_logs ADD CONSTRAINT ck_admin_audit_action
    CHECK (action IN ('VIEW_CLAIM_DETAIL', 'VIEW_CLAIM_ATTACHMENTS', 'VIEW_CLAIM_MESSAGES',
                      'VIEW_CLAIM_RECEIPT',
                      'APPROVE_ORGANIZATION', 'REJECT_ORGANIZATION',
                      'SUSPEND_ORGANIZATION', 'REACTIVATE_ORGANIZATION',
                      'VIEW_ORGANIZATION_DOCUMENTS',
                      'DELETE_ATTACHMENT', 'DELETE_ORGANIZATION_DOCUMENT',
                      'RUN_RELEASE_SWEEP', 'RUN_ATTACHMENT_CLEANUP', 'RUN_DEADLINE_REMINDERS',
                      'APPROVE_WISH', 'REJECT_WISH',
                      'FREEZE_WISH', 'UNFREEZE_WISH'));
