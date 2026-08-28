-- =============================================================================
-- 平台管理員的稽核軌跡
--
-- 管理員為了處理申訴與排查問題，需要跨機構檢視願望與認領，其中包含捐贈者個資、
-- 寄送證明，以及可能含孩童影像的回饋照片。這是整個系統中權限最大的角色。
--
-- 「能看」與「看了不留痕跡」是兩件事。這張表記錄每一次敏感存取，讓管理員的行為
-- 本身也是可被檢視的——包括在監控中心裡對所有管理員公開。
-- =============================================================================

CREATE TABLE admin_audit_logs (
    id             bigint        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    admin_user_id  uuid          NOT NULL,
    action         varchar(40)   NOT NULL,

    -- 被存取的對象。target_id 可為 null（例如觸發排程這種不針對特定資源的動作）
    target_type    varchar(30)   NOT NULL,
    target_id      uuid,

    -- 補充說明：審核意見、掃描結果摘要等
    detail         varchar(500),
    created_at     timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT ck_admin_audit_action
        CHECK (action IN ('VIEW_CLAIM_DETAIL', 'VIEW_CLAIM_ATTACHMENTS',
                          'APPROVE_ORGANIZATION', 'REJECT_ORGANIZATION',
                          'RUN_RELEASE_SWEEP')),
    CONSTRAINT ck_admin_audit_target_type
        CHECK (target_type IN ('CLAIM', 'ORGANIZATION', 'SYSTEM')),
    -- 只有 SYSTEM 類的動作可以沒有目標
    CONSTRAINT ck_admin_audit_target_id
        CHECK (target_type = 'SYSTEM' OR target_id IS NOT NULL),

    CONSTRAINT fk_admin_audit_admin
        FOREIGN KEY (admin_user_id) REFERENCES users (id) ON DELETE RESTRICT
);

-- 監控中心的預設檢視：最近的動作
CREATE INDEX idx_admin_audit_recent ON admin_audit_logs (created_at DESC);

-- 「這位管理員做過什麼」與「這筆資料被誰看過」兩種查法
CREATE INDEX idx_admin_audit_by_admin ON admin_audit_logs (admin_user_id, created_at DESC);
CREATE INDEX idx_admin_audit_by_target ON admin_audit_logs (target_type, target_id, created_at DESC);
