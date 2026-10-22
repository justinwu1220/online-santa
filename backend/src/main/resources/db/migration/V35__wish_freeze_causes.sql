-- 凍結／停權處置的地基：願望的凍結改成「多原因」，三種情境（內容疑慮、機構停權、認領者停權）可以疊加，
-- 只有所有原因都解除，願望才真的解除凍結、寄送期限才恢復。
--
-- wish_freezes 是凍結的歷史與現況（append-only，解除只填 lifted_at）；
-- wishes.frozen_at / frozen_reason / frozen_by 仍保留為反正規化欄位，由 service 維護，
-- 現有的願望牆過濾與 isFrozen() 都不用動；wishes.frozen_cause 是目前優先度最高的原因。

CREATE TABLE wish_freezes (
    id          bigint        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    wish_id     uuid          NOT NULL,
    cause       varchar(20)   NOT NULL,
    reason      text          NOT NULL,
    created_by  uuid,                       -- 系統動作（機構停權連動）為 NULL
    created_at  timestamptz   NOT NULL DEFAULT now(),
    lifted_at   timestamptz,
    lifted_by   uuid,

    CONSTRAINT ck_wish_freezes_cause
        CHECK (cause IN ('ADMIN_CONTENT', 'ADMIN_OTHER', 'ORG_SUSPENDED', 'DONOR_SUSPENDED')),
    CONSTRAINT fk_wish_freezes_wish
        FOREIGN KEY (wish_id) REFERENCES wishes (id) ON DELETE CASCADE
);

-- 同一個願望、同一個原因同時只能有一筆進行中的凍結
CREATE UNIQUE INDEX uq_wish_freezes_active ON wish_freezes (wish_id, cause) WHERE lifted_at IS NULL;
CREATE INDEX idx_wish_freezes_wish ON wish_freezes (wish_id, created_at);

ALTER TABLE wishes ADD COLUMN frozen_cause varchar(20);
ALTER TABLE wishes ADD CONSTRAINT ck_wishes_frozen_cause
    CHECK (frozen_cause IS NULL OR frozen_cause IN ('ADMIN_CONTENT', 'ADMIN_OTHER', 'ORG_SUSPENDED', 'DONOR_SUSPENDED'));

-- 既有的凍結都是管理員的單純凍結
INSERT INTO wish_freezes (wish_id, cause, reason, created_by, created_at)
SELECT id, 'ADMIN_OTHER', frozen_reason, frozen_by, frozen_at FROM wishes WHERE frozen_at IS NOT NULL;
UPDATE wishes SET frozen_cause = 'ADMIN_OTHER' WHERE frozen_at IS NOT NULL;

-- 稽核：新增停權、改內容、釋出認領等動作，稽核對象新增 USER
ALTER TABLE admin_audit_logs DROP CONSTRAINT ck_admin_audit_action;
ALTER TABLE admin_audit_logs ADD CONSTRAINT ck_admin_audit_action
    CHECK (action IN ('VIEW_CLAIM_DETAIL', 'VIEW_CLAIM_ATTACHMENTS', 'VIEW_CLAIM_MESSAGES',
                      'VIEW_CLAIM_RECEIPT',
                      'APPROVE_ORGANIZATION', 'REJECT_ORGANIZATION',
                      'SUSPEND_ORGANIZATION', 'REACTIVATE_ORGANIZATION',
                      'VIEW_ORGANIZATION_DOCUMENTS', 'VIEW_ORGANIZATION_PHOTO',
                      'DELETE_ATTACHMENT', 'DELETE_ORGANIZATION_DOCUMENT',
                      'RUN_RELEASE_SWEEP', 'RUN_ATTACHMENT_CLEANUP', 'RUN_DEADLINE_REMINDERS',
                      'APPROVE_WISH', 'REJECT_WISH',
                      'FREEZE_WISH', 'UNFREEZE_WISH', 'VIEW_WISH_ATTACHMENTS',
                      'SUSPEND_USER', 'REACTIVATE_USER', 'EDIT_WISH_CONTENT', 'RELEASE_CLAIM_BY_ADMIN',
                      'DECIDE_UNLOCK_REQUEST', 'NOTIFY_AFFECTED_DONORS', 'VIEW_SUSPENSION_RECORD',
                      'REDACT_MESSAGE'));

ALTER TABLE admin_audit_logs DROP CONSTRAINT ck_admin_audit_target_type;
ALTER TABLE admin_audit_logs ADD CONSTRAINT ck_admin_audit_target_type
    CHECK (target_type IN ('CLAIM', 'ORGANIZATION', 'WISH', 'USER', 'SYSTEM'));
