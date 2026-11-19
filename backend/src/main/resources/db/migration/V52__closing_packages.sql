-- =============================================================================
-- 結案文件包（成果備查 C、結案備查 D、併報 CD）與檔期財務資料。
--   1. closing_exports 新增 kind＝CLOSING_PACKAGE 與 stage（C／D／CD）。
--   2. campaign_finance：每個月的現金實募、利息、募得款實支，由管理員手動填寫（系統沒有這些資料）。
--      空白（NULL）代表「待補」，文件上寫待補；填 0 代表已核實真的沒有發生，兩者不能混用。
--   3. campaigns 新增自籌金額與說明（同樣 NULL＝待補）。
-- =============================================================================
ALTER TABLE closing_exports ADD COLUMN stage varchar(4);
ALTER TABLE closing_exports DROP CONSTRAINT ck_closing_exports_kind;
ALTER TABLE closing_exports ADD CONSTRAINT ck_closing_exports_kind CHECK (kind IN ('RECEIPTS', 'CLOSING_PACKAGE'));
ALTER TABLE closing_exports ADD CONSTRAINT ck_closing_exports_stage CHECK (stage IS NULL OR stage IN ('C', 'D', 'CD'));

CREATE TABLE campaign_finance (
    id             uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id    uuid         NOT NULL,
    period_year    integer      NOT NULL,
    period_month   integer      NOT NULL,
    cash_income    bigint,
    interest       bigint,
    cash_expense   bigint,
    note           varchar(300),
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT ck_campaign_finance_month CHECK (period_month BETWEEN 1 AND 12),
    CONSTRAINT ck_campaign_finance_amounts CHECK (
        (cash_income IS NULL OR cash_income >= 0) AND (interest IS NULL OR interest >= 0)
        AND (cash_expense IS NULL OR cash_expense >= 0)),
    CONSTRAINT fk_campaign_finance_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns (id) ON DELETE CASCADE,
    CONSTRAINT uq_campaign_finance_month UNIQUE (campaign_id, period_year, period_month)
);

CREATE TRIGGER trg_campaign_finance_updated_at
    BEFORE UPDATE ON campaign_finance
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE campaigns ADD COLUMN self_funded_amount bigint;
ALTER TABLE campaigns ADD COLUMN self_funded_note varchar(500);
ALTER TABLE campaigns ADD CONSTRAINT ck_campaigns_self_funded CHECK (self_funded_amount IS NULL OR self_funded_amount >= 0);
