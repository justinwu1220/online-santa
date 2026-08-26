-- =============================================================================
-- 線上聖誕老公公 — 初始 schema
--
-- 隱私設計原則：本 schema 刻意「不存在」孩童姓名、生日、住址、照片等可識別
-- 欄位。願望只以暱稱（child_alias）、年齡區間（age_range）與興趣描述呈現。
-- 沒有欄位可存，就沒有外洩的可能——這比僅靠應用層規範更可靠。
--
-- 時間一律以 UTC（timestamptz）儲存，由前端負責在地化顯示。
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 通用：自動維護 updated_at
-- 認領流程包含繞過 Hibernate 生命週期的原生 UPDATE，因此用觸發器保證一致。
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $fn$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$fn$ LANGUAGE plpgsql;


-- -----------------------------------------------------------------------------
-- organizations — 兒童機構（賣家）
-- -----------------------------------------------------------------------------
CREATE TABLE organizations (
    id                  uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    name                varchar(120)  NOT NULL,
    contact_email       varchar(255)  NOT NULL,
    contact_phone       varchar(40),
    address             varchar(255),
    description         text,

    -- 審核狀態：自助註冊後為 PENDING，須由 ADMIN 核准才能上架願望
    status              varchar(20)   NOT NULL DEFAULT 'PENDING',
    review_note         text,
    reviewed_by         uuid,
    reviewed_at         timestamptz,

    -- 認領逾期的處理政策，套用該機構的全部願望
    --   MANUAL：系統僅標記逾期，由機構在後台自行決定是否釋回
    --   AUTO  ：逾期未上傳寄送證明即自動釋回，願望重新上架
    release_policy      varchar(10)   NOT NULL DEFAULT 'MANUAL',
    release_after_days  integer,

    created_at          timestamptz   NOT NULL DEFAULT now(),
    updated_at          timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT ck_org_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED')),
    CONSTRAINT ck_org_release_policy
        CHECK (release_policy IN ('MANUAL', 'AUTO')),
    -- AUTO 政策必須指定寬限天數；MANUAL 則不得指定，避免語意含糊
    CONSTRAINT ck_org_release_after_days
        CHECK (
            (release_policy = 'AUTO'   AND release_after_days BETWEEN 1 AND 60)
         OR (release_policy = 'MANUAL' AND release_after_days IS NULL)
        )
);

CREATE UNIQUE INDEX uq_organizations_name ON organizations (lower(name));
CREATE INDEX idx_organizations_pending ON organizations (created_at)
    WHERE status = 'PENDING';

CREATE TRIGGER trg_organizations_updated_at
    BEFORE UPDATE ON organizations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- -----------------------------------------------------------------------------
-- users — 由 Firebase Authentication 對應而來的本地帳號
--
-- 授權的權威來源是本表，而非 Firebase custom claims：claims 需等 ID token
-- 刷新（約 1 小時）才生效，會讓「Admin 剛核准的機構」卡住無法操作。
-- -----------------------------------------------------------------------------
CREATE TABLE users (
    id              uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    firebase_uid    varchar(128)  NOT NULL,
    email           varchar(255)  NOT NULL,
    display_name    varchar(100),

    role            varchar(20)   NOT NULL DEFAULT 'DONOR',
    organization_id uuid,

    disabled        boolean       NOT NULL DEFAULT false,
    last_login_at   timestamptz,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT ck_users_role
        CHECK (role IN ('DONOR', 'ORG_MEMBER', 'ADMIN')),
    -- 只有機構成員可以隸屬於機構
    CONSTRAINT ck_users_org_membership
        CHECK (
            (role = 'ORG_MEMBER' AND organization_id IS NOT NULL)
         OR (role <> 'ORG_MEMBER' AND organization_id IS NULL)
        ),
    CONSTRAINT fk_users_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_users_firebase_uid ON users (firebase_uid);
CREATE UNIQUE INDEX uq_users_email ON users (lower(email));
CREATE INDEX idx_users_organization ON users (organization_id)
    WHERE organization_id IS NOT NULL;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- organizations.reviewed_by 的外鍵須待 users 建立後才能加上（循環相依）
ALTER TABLE organizations
    ADD CONSTRAINT fk_organizations_reviewed_by
    FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE SET NULL;


-- -----------------------------------------------------------------------------
-- wishes — 孩童願望
--
-- status 為列表查詢用的粗粒度狀態（有索引支撐搶領當日的瀏覽流量）；
-- 細節流程狀態記在 claims.status，兩者由 ClaimService 於同一交易內同步維護。
-- -----------------------------------------------------------------------------
CREATE TABLE wishes (
    id              uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid          NOT NULL,

    -- === 孩童資訊：僅限去識別化欄位 ===
    child_alias     varchar(50)   NOT NULL,   -- 暱稱，非真實姓名
    age_range       varchar(20)   NOT NULL,   -- 年齡「區間」，非生日或實歲
    interests       varchar(500),             -- 興趣描述

    -- === 願望內容 ===
    title           varchar(120)  NOT NULL,
    description     text,
    category        varchar(30)   NOT NULL,
    price_range     varchar(20)   NOT NULL,

    status          varchar(20)   NOT NULL DEFAULT 'DRAFT',

    -- 樂觀鎖版本號：認領時的原子條件 UPDATE 會遞增此欄位
    version         bigint        NOT NULL DEFAULT 0,

    published_at    timestamptz,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT ck_wishes_status
        CHECK (status IN ('DRAFT', 'AVAILABLE', 'CLAIMED', 'FULFILLED', 'ARCHIVED')),
    CONSTRAINT ck_wishes_age_range
        CHECK (age_range IN ('AGE_0_3', 'AGE_4_6', 'AGE_7_9',
                             'AGE_10_12', 'AGE_13_15', 'AGE_16_18')),
    CONSTRAINT ck_wishes_category
        CHECK (category IN ('TOY', 'BOOK', 'CLOTHING', 'SPORTS', 'STATIONERY',
                            'ELECTRONICS', 'MUSIC', 'ART', 'DAILY_NECESSITIES', 'OTHER')),
    CONSTRAINT ck_wishes_price_range
        CHECK (price_range IN ('UNDER_500', 'FROM_500_TO_1000',
                               'FROM_1000_TO_2000', 'OVER_2000')),
    -- 已離開草稿狀態的願望必定有發布時間
    CONSTRAINT ck_wishes_published_at
        CHECK (status = 'DRAFT' OR published_at IS NOT NULL),
    CONSTRAINT fk_wishes_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT
);

-- 搶領當日的主要查詢路徑：只掃可認領的願望，依發布時間排序
CREATE INDEX idx_wishes_browse
    ON wishes (published_at DESC, id)
    WHERE status = 'AVAILABLE';

-- 公開列表的篩選條件
CREATE INDEX idx_wishes_filter
    ON wishes (category, age_range)
    WHERE status = 'AVAILABLE';

-- 機構後台：列出自己的全部願望
CREATE INDEX idx_wishes_organization ON wishes (organization_id, status);

CREATE TRIGGER trg_wishes_updated_at
    BEFORE UPDATE ON wishes
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- -----------------------------------------------------------------------------
-- claims — 認領紀錄（等同交易平台的訂單）
-- -----------------------------------------------------------------------------
CREATE TABLE claims (
    id                       uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    wish_id                  uuid          NOT NULL,
    donor_user_id            uuid          NOT NULL,

    status                   varchar(20)   NOT NULL DEFAULT 'CLAIMED',

    -- 認領當下「快照」機構的釋回政策。機構事後修改設定只影響新的認領，
    -- 不溯及既往，避免已認領者的期限被無預警縮短。
    release_policy_snapshot  varchar(10)   NOT NULL,
    ship_deadline_at         timestamptz,

    claimed_at               timestamptz   NOT NULL DEFAULT now(),
    shipped_at               timestamptz,
    received_at              timestamptz,
    completed_at             timestamptz,
    released_at              timestamptz,
    release_reason           varchar(255),

    tracking_carrier         varchar(60),
    tracking_number          varchar(80),
    donor_message            varchar(500),   -- 給孩子的話

    version                  bigint        NOT NULL DEFAULT 0,
    created_at               timestamptz   NOT NULL DEFAULT now(),
    updated_at               timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT ck_claims_status
        CHECK (status IN ('CLAIMED', 'SHIPPED', 'RECEIVED',
                          'COMPLETED', 'RELEASED', 'CANCELLED')),
    CONSTRAINT ck_claims_release_policy_snapshot
        CHECK (release_policy_snapshot IN ('MANUAL', 'AUTO')),
    -- AUTO 政策必定算得出寄送期限
    CONSTRAINT ck_claims_ship_deadline
        CHECK (release_policy_snapshot = 'MANUAL' OR ship_deadline_at IS NOT NULL),
    CONSTRAINT fk_claims_wish
        FOREIGN KEY (wish_id) REFERENCES wishes (id) ON DELETE RESTRICT,
    CONSTRAINT fk_claims_donor
        FOREIGN KEY (donor_user_id) REFERENCES users (id) ON DELETE RESTRICT
);

-- ★ 防超賣的最後防線 ★
-- 應用層以原子條件 UPDATE（樂觀鎖）擋下併發搶領；此部分唯一索引則保證即使有
-- 程式碼繞過 ClaimService（手寫 SQL、日後新增的端點），同一願望也絕不可能
-- 同時存在兩筆有效認領。RELEASED / CANCELLED 不佔用名額，故排除在外。
CREATE UNIQUE INDEX uq_active_claim_per_wish
    ON claims (wish_id)
    WHERE status IN ('CLAIMED', 'SHIPPED', 'RECEIVED', 'COMPLETED');

-- 「我的認領」頁面，以及每位民眾的進行中認領數上限檢查
CREATE INDEX idx_claims_donor ON claims (donor_user_id, status, claimed_at DESC);

-- 釋回排程：掃出逾期未寄送的認領
CREATE INDEX idx_claims_overdue
    ON claims (ship_deadline_at)
    WHERE status = 'CLAIMED' AND ship_deadline_at IS NOT NULL;

CREATE TRIGGER trg_claims_updated_at
    BEFORE UPDATE ON claims
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- -----------------------------------------------------------------------------
-- claim_events — 認領歷程的稽核軌跡（append-only）
-- -----------------------------------------------------------------------------
CREATE TABLE claim_events (
    id             bigint        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id       uuid          NOT NULL,
    event_type     varchar(30)   NOT NULL,
    actor_user_id  uuid,                       -- 由排程觸發時為 NULL（系統）
    note           varchar(500),
    created_at     timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT ck_claim_events_type
        CHECK (event_type IN ('CLAIMED', 'SHIPPED', 'RECEIVED', 'COMPLETED',
                              'RELEASED_MANUAL', 'RELEASED_AUTO', 'CANCELLED',
                              'FEEDBACK_UPLOADED')),
    CONSTRAINT fk_claim_events_claim
        FOREIGN KEY (claim_id) REFERENCES claims (id) ON DELETE CASCADE,
    CONSTRAINT fk_claim_events_actor
        FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_claim_events_claim ON claim_events (claim_id, created_at);


-- -----------------------------------------------------------------------------
-- attachments — Cloud Storage 物件的中繼資料
--
-- 檔案由前端透過 Signed URL 直傳，不經過 API；本表記錄物件位置與確認狀態。
-- upload_status = PENDING 代表已發出 Signed URL 但尚未確認上傳成功。
-- -----------------------------------------------------------------------------
CREATE TABLE attachments (
    id             uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_type     varchar(30)   NOT NULL,
    owner_id       uuid          NOT NULL,     -- wish_id 或 claim_id，依 owner_type 而定
    object_name    varchar(500)  NOT NULL,     -- GCS 物件路徑
    content_type   varchar(100)  NOT NULL,
    size_bytes     bigint,
    upload_status  varchar(20)   NOT NULL DEFAULT 'PENDING',
    uploaded_by    uuid,
    confirmed_at   timestamptz,
    created_at     timestamptz   NOT NULL DEFAULT now(),
    updated_at     timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT ck_attachments_owner_type
        CHECK (owner_type IN ('WISH_IMAGE', 'SHIPPING_PROOF', 'ORG_FEEDBACK')),
    CONSTRAINT ck_attachments_upload_status
        CHECK (upload_status IN ('PENDING', 'CONFIRMED')),
    CONSTRAINT ck_attachments_confirmed_at
        CHECK (upload_status = 'PENDING' OR confirmed_at IS NOT NULL),
    CONSTRAINT fk_attachments_uploaded_by
        FOREIGN KEY (uploaded_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX uq_attachments_object_name ON attachments (object_name);
CREATE INDEX idx_attachments_owner ON attachments (owner_type, owner_id)
    WHERE upload_status = 'CONFIRMED';
-- 清理未完成上傳的孤兒紀錄
CREATE INDEX idx_attachments_pending ON attachments (created_at)
    WHERE upload_status = 'PENDING';

CREATE TRIGGER trg_attachments_updated_at
    BEFORE UPDATE ON attachments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- -----------------------------------------------------------------------------
-- messages — 認領者與機構之間的對話（限定於單筆認領內）
-- -----------------------------------------------------------------------------
CREATE TABLE messages (
    id             bigint        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id       uuid          NOT NULL,
    sender_user_id uuid          NOT NULL,
    body           varchar(2000) NOT NULL,
    read_at        timestamptz,
    created_at     timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_messages_claim
        FOREIGN KEY (claim_id) REFERENCES claims (id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_sender
        FOREIGN KEY (sender_user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_messages_claim ON messages (claim_id, created_at);
CREATE INDEX idx_messages_unread ON messages (claim_id) WHERE read_at IS NULL;
