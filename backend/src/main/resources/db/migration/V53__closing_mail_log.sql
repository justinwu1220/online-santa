-- =============================================================================
-- 結案寄信紀錄：機構確認表（05A＋05B）寄給誰、成功或失敗。
--   紅聯收據的寄送狀態在 donation_receipts（red_sent_at／red_send_error），不重複記。
--   （檔期、種類、對象）唯一：同一個機構同一種信不會重複寄；失敗的可以重寄（更新同一列）。
--   只存收件信箱與狀態，不存附件內容。
-- =============================================================================
CREATE TABLE closing_mail_log (
    id           uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id  uuid         NOT NULL,
    kind         varchar(20)  NOT NULL,
    target_id    uuid         NOT NULL,
    recipient    varchar(255) NOT NULL,
    status       varchar(10)  NOT NULL,
    error        varchar(300),
    sent_at      timestamptz,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT ck_closing_mail_log_kind CHECK (kind IN ('ORG_FORMS')),
    CONSTRAINT ck_closing_mail_log_status CHECK (status IN ('SENT', 'FAILED')),
    CONSTRAINT fk_closing_mail_log_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns (id) ON DELETE CASCADE,
    CONSTRAINT uq_closing_mail_log_target UNIQUE (campaign_id, kind, target_id)
);
