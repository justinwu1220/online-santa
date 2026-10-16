-- 捐贈收據：認領者回報寄送時可選擇申請，資料只有平台管理員看得到。
--
-- 收據資料放在獨立的 claim_receipts 表，而不是 claims 的欄位：機構端與捐贈者端的
-- 回應型別都從 Claim 實體轉出，資料不在實體上就不可能被誤帶進那些回應。
-- claims 只多一個非敏感的旗標 receipt_requested，供管理員列表篩選與捐贈者畫面顯示
-- 「已申請」；機構端的型別刻意不含這個旗標。
--
-- 身分證字號／統一編號以 AES-256-GCM 加密後存放（id_number_encrypted），金鑰由
-- 應用層提供（RECEIPT_ENCRYPTION_KEY），資料庫與備份本身看不到明文。姓名、信箱、
-- 地址是一般個資，明文存放，但同樣只有管理端專用的端點讀得到。

ALTER TABLE claims ADD COLUMN receipt_requested boolean NOT NULL DEFAULT false;

CREATE TABLE claim_receipts (
    id                  uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id            uuid          NOT NULL,
    name                varchar(100)  NOT NULL,
    id_number_encrypted varchar(255)  NOT NULL,
    email               varchar(255)  NOT NULL,
    address             varchar(255)  NOT NULL,
    created_at          timestamptz   NOT NULL DEFAULT now(),
    updated_at          timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT uq_claim_receipts_claim UNIQUE (claim_id),
    CONSTRAINT fk_claim_receipts_claim
        FOREIGN KEY (claim_id) REFERENCES claims (id) ON DELETE RESTRICT
);

-- 發票證明（RECEIPT_PROOF）：掛在認領上，私密 bucket，只有管理端點讀得到
ALTER TABLE attachments DROP CONSTRAINT ck_attachments_owner_type;

ALTER TABLE attachments ADD CONSTRAINT ck_attachments_owner_type
    CHECK (owner_type IN ('WISH_IMAGE', 'SHIPPING_PROOF', 'ORG_FEEDBACK',
                          'ORG_DOCUMENT', 'WISH_LETTER', 'RECEIPT_PROOF'));

-- 管理員檢視捐贈收據資料，新增稽核動作
ALTER TABLE admin_audit_logs DROP CONSTRAINT ck_admin_audit_action;

ALTER TABLE admin_audit_logs ADD CONSTRAINT ck_admin_audit_action
    CHECK (action IN ('VIEW_CLAIM_DETAIL', 'VIEW_CLAIM_ATTACHMENTS', 'VIEW_CLAIM_MESSAGES',
                      'VIEW_CLAIM_RECEIPT',
                      'APPROVE_ORGANIZATION', 'REJECT_ORGANIZATION',
                      'SUSPEND_ORGANIZATION', 'REACTIVATE_ORGANIZATION',
                      'VIEW_ORGANIZATION_DOCUMENTS',
                      'DELETE_ATTACHMENT', 'DELETE_ORGANIZATION_DOCUMENT',
                      'RUN_RELEASE_SWEEP', 'RUN_ATTACHMENT_CLEANUP',
                      'RUN_DEADLINE_REMINDERS'));
