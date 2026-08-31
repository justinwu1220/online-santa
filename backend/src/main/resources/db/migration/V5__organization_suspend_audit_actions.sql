-- 管理端停權/復權機構會新增兩種稽核動作，CHECK 約束要跟著放寬。
--
-- Postgres 不能直接 ALTER 一個 CHECK 約束，只能砍掉重建。

ALTER TABLE admin_audit_logs DROP CONSTRAINT ck_admin_audit_action;

ALTER TABLE admin_audit_logs ADD CONSTRAINT ck_admin_audit_action
    CHECK (action IN ('VIEW_CLAIM_DETAIL', 'VIEW_CLAIM_ATTACHMENTS',
                      'APPROVE_ORGANIZATION', 'REJECT_ORGANIZATION',
                      'SUSPEND_ORGANIZATION', 'REACTIVATE_ORGANIZATION',
                      'RUN_RELEASE_SWEEP'));
