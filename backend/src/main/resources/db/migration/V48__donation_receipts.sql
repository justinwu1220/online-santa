-- =============================================================================
-- 捐贈收據：每個完成的願望一張。開立當下把需要印的資料存成「快照」，之後收據的內容
-- 不隨捐贈者改姓名、機構改名稱而變；PDF 只是這份快照的呈現，可隨時重新產生且內容相同。
--
-- 一個願望同時只有一張有效（ISSUED）收據；作廢（VOIDED）的保留原號與替代關係，
-- 號碼不再配給別人（號碼由 campaigns.next_receipt_seq 單調遞增）。
-- =============================================================================
CREATE TABLE donation_receipts (
    id                   uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id             uuid          NOT NULL,
    campaign_id          uuid          NOT NULL,
    receipt_no           varchar(40)   NOT NULL,
    status               varchar(20)   NOT NULL DEFAULT 'ISSUED',
    issued_on            date          NOT NULL,

    -- ── 開立當下的快照 ──
    -- 捐贈者勾選要收據（才會把白聯寄給他）；沒勾選也開，只是不寄
    receipt_requested    boolean       NOT NULL,
    -- 勾選要收據者用他填的收據抬頭，否則用帳號的真實姓名，兩者都沒有就是「善心人士」
    donor_name           varchar(100)  NOT NULL,
    -- 證號沿用 claim_receipts 的密文（AES-256-GCM），不在這裡存明文；沒勾選要收據就沒有
    donor_id_encrypted   varchar(255),
    donor_email          varchar(255),
    donated_on           date          NOT NULL,
    received_on          date,
    gift_description     varchar(200)  NOT NULL,
    gift_quantity        integer       NOT NULL,
    gift_unit            varchar(10)   NOT NULL DEFAULT '份',
    -- 時價：認領者寄出時填的「禮物價格」（購買發票金額，整份禮物的總額），新臺幣元
    gift_value           integer       NOT NULL,
    organization_id      uuid          NOT NULL,
    organization_name    varchar(120)  NOT NULL,
    organization_tax_id  varchar(8),

    -- ── 第一次產生 PDF 時凍結的檔期資訊 ──
    -- 許可文號可能在收據開立之後才核准或更正；PDF 一旦產生過，之後重新產生一律用當時的值，
    -- 免得同一張收據前後兩份內容不同
    permit_no_used          varchar(100),
    activity_name_used      varchar(200),
    collection_start_used   date,
    collection_end_used     date,

    -- ── PDF ──
    -- PENDING 待產生｜PENDING_INFO 檔期尚缺許可文號｜READY 已產生｜FAILED 版面放不下等
    pdf_status           varchar(20)   NOT NULL DEFAULT 'PENDING',
    pdf_error            varchar(500),
    white_object         varchar(255),
    yellow_red_object    varchar(255),
    pdf_generated_at     timestamptz,
    pdf_expires_at       timestamptz,

    -- ── 作廢 ──
    void_reason          varchar(500),
    voided_at            timestamptz,
    replaced_by_id       uuid,

    -- ── 白聯寄送 ──
    white_sent_at        timestamptz,
    white_send_error     varchar(500),

    created_at           timestamptz   NOT NULL DEFAULT now(),
    updated_at           timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT ck_donation_receipts_status CHECK (status IN ('ISSUED', 'VOIDED')),
    CONSTRAINT ck_donation_receipts_pdf_status
        CHECK (pdf_status IN ('PENDING', 'PENDING_INFO', 'READY', 'FAILED')),
    CONSTRAINT ck_donation_receipts_quantity CHECK (gift_quantity >= 1),
    CONSTRAINT ck_donation_receipts_value CHECK (gift_value >= 0),
    -- 作廢必須有原因與時間
    CONSTRAINT ck_donation_receipts_void
        CHECK (status = 'ISSUED' OR (void_reason IS NOT NULL AND voided_at IS NOT NULL)),
    CONSTRAINT fk_donation_receipts_claim
        FOREIGN KEY (claim_id) REFERENCES claims (id) ON DELETE RESTRICT,
    CONSTRAINT fk_donation_receipts_campaign
        FOREIGN KEY (campaign_id) REFERENCES campaigns (id) ON DELETE RESTRICT,
    CONSTRAINT fk_donation_receipts_replaced_by
        FOREIGN KEY (replaced_by_id) REFERENCES donation_receipts (id) ON DELETE SET NULL
);

-- 編號全站唯一（含作廢的）：作廢的號碼不能再配給別人
CREATE UNIQUE INDEX uq_donation_receipts_no ON donation_receipts (receipt_no);
-- 一個願望同時只有一張有效收據；作廢後才能重開
CREATE UNIQUE INDEX uq_donation_receipts_claim_valid ON donation_receipts (claim_id) WHERE status = 'ISSUED';
CREATE INDEX idx_donation_receipts_campaign ON donation_receipts (campaign_id, receipt_no);
CREATE INDEX idx_donation_receipts_pdf_pending ON donation_receipts (campaign_id)
    WHERE status = 'ISSUED' AND pdf_status <> 'READY';

CREATE TRIGGER trg_donation_receipts_updated_at
    BEFORE UPDATE ON donation_receipts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
