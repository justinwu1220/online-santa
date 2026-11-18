-- =============================================================================
-- 活動檔期（結案文件的統計依據）與結案相關的稽核動作。
--
-- 一個檔期＝一次公益勸募活動：勸募期間（收捐贈、開收據）、財物使用期間、統計截止日、
-- 許可文號、收據編號的前綴與流水號。管理員在後台設定，結案文件的統計與收據都以它為準。
--
-- 收據流水號放在這張表上（next_receipt_seq），配號時以 SELECT ... FOR UPDATE 鎖住該列再遞增：
-- 同時有兩個願望完成也不會拿到同一個號碼；號碼只增不減，作廢的號碼不再配給別人。
-- =============================================================================
CREATE TABLE campaigns (
    id                  uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    name                varchar(100)  NOT NULL,
    -- 主管機關系統的活動編號（例如 115000877）
    activity_no         varchar(30),
    -- 勸募許可文號：核准前是空的。空的時候開立的收據只存資料、不產生正式 PDF（標「待補」）
    permit_no           varchar(100),
    -- 核准文件上的活動全名，印在收據與各文件上
    approved_name       varchar(200),

    collection_start    date          NOT NULL,
    collection_end      date          NOT NULL,
    use_start           date          NOT NULL,
    use_end             date          NOT NULL,
    -- 統計截止日；空白時文件以勸募結束日為截止日
    cutoff_date         date,

    receipt_prefix      varchar(20)   NOT NULL,
    next_receipt_seq    integer       NOT NULL DEFAULT 1,
    -- 已配出的收據數。只用來判斷「配號設定還能不能改」，不當作統計來源
    receipts_issued     integer       NOT NULL DEFAULT 0,

    -- 代表協會自身服務對象的機構帳號（05C 的對象）；沒有就不產 05C
    association_org_id  uuid,

    status              varchar(20)   NOT NULL DEFAULT 'ACTIVE',

    created_at          timestamptz   NOT NULL DEFAULT now(),
    updated_at          timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT ck_campaigns_status CHECK (status IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT ck_campaigns_collection CHECK (collection_end >= collection_start),
    CONSTRAINT ck_campaigns_use CHECK (use_end >= use_start),
    CONSTRAINT ck_campaigns_prefix CHECK (receipt_prefix ~ '^[A-Z0-9]{2,20}$'),
    CONSTRAINT ck_campaigns_seq CHECK (next_receipt_seq >= 1 AND receipts_issued >= 0),
    CONSTRAINT fk_campaigns_association_org
        FOREIGN KEY (association_org_id) REFERENCES organizations (id) ON DELETE SET NULL
);

-- 收據編號的前綴不可與別的檔期重複，否則兩個檔期會發出同一個編號
CREATE UNIQUE INDEX uq_campaigns_receipt_prefix ON campaigns (receipt_prefix);

CREATE TRIGGER trg_campaigns_updated_at
    BEFORE UPDATE ON campaigns
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- -----------------------------------------------------------------------------
-- 稽核動作：結案文件相關的操作一次全部登記，之後的階段不必再重寫這條約束。
-- 內容是 V43 的完整清單加上新值。
-- -----------------------------------------------------------------------------
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
                      'REDACT_MESSAGE', 'DELETE_WISH_LETTER',
                      'VIEW_USER_PROFILE_CHANGES', 'VIEW_ORGANIZATION_PROFILE_CHANGES',
                      'VIEW_PROFILE_CHANGE_LOG',
                      'SAVE_CAMPAIGN', 'VOID_RECEIPT', 'VIEW_RECEIPT_DOCUMENT',
                      'CREATE_CLOSING_EXPORT', 'DOWNLOAD_CLOSING_EXPORT',
                      'SEND_RECEIPT_EMAILS', 'SEND_ORG_FORM_EMAILS'));
