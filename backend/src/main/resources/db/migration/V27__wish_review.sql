-- 1018 新版：願望審核流程（機構提交審核，管理員核准／退回）。
--
-- 狀態：DRAFT（草稿）→ PENDING_REVIEW（審核中）→ AVAILABLE（可認領）。
-- 管理員退回時願望回到 DRAFT 並留下原因。機構端不再能下架（ARCHIVED 拿掉），
-- 已下架的舊願望轉回草稿，由機構重新提交審核。

UPDATE wishes SET status = 'DRAFT' WHERE status = 'ARCHIVED';

ALTER TABLE wishes DROP CONSTRAINT ck_wishes_status;
ALTER TABLE wishes ADD CONSTRAINT ck_wishes_status
    CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'AVAILABLE', 'CLAIMED', 'FULFILLED'));

-- 草稿與審核中都還沒公開，所以沒有發布時間；上架（核准）之後一定有
ALTER TABLE wishes DROP CONSTRAINT ck_wishes_published_at;
ALTER TABLE wishes ADD CONSTRAINT ck_wishes_published_at
    CHECK (status IN ('DRAFT', 'PENDING_REVIEW') OR published_at IS NOT NULL);

ALTER TABLE wishes ADD COLUMN review_note text;
ALTER TABLE wishes ADD COLUMN reviewed_at timestamptz;
ALTER TABLE wishes ADD COLUMN reviewed_by uuid;

-- 稽核：核准、退回願望
ALTER TABLE admin_audit_logs DROP CONSTRAINT ck_admin_audit_action;
ALTER TABLE admin_audit_logs ADD CONSTRAINT ck_admin_audit_action
    CHECK (action IN ('VIEW_CLAIM_DETAIL', 'VIEW_CLAIM_ATTACHMENTS', 'VIEW_CLAIM_MESSAGES',
                      'VIEW_CLAIM_RECEIPT',
                      'APPROVE_ORGANIZATION', 'REJECT_ORGANIZATION',
                      'SUSPEND_ORGANIZATION', 'REACTIVATE_ORGANIZATION',
                      'VIEW_ORGANIZATION_DOCUMENTS',
                      'DELETE_ATTACHMENT', 'DELETE_ORGANIZATION_DOCUMENT',
                      'RUN_RELEASE_SWEEP', 'RUN_ATTACHMENT_CLEANUP', 'RUN_DEADLINE_REMINDERS',
                      'APPROVE_WISH', 'REJECT_WISH'));

ALTER TABLE admin_audit_logs DROP CONSTRAINT ck_admin_audit_target_type;
ALTER TABLE admin_audit_logs ADD CONSTRAINT ck_admin_audit_target_type
    CHECK (target_type IN ('CLAIM', 'ORGANIZATION', 'WISH', 'SYSTEM'));
