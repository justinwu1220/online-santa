-- 參與須知的同意紀錄。
--
-- 只增不改：每次同意都新增一列，不覆蓋舊的。須知日後會改版，「這個人當時同意的是哪個版本」
-- 必須永遠查得到；若只存「最新版本」，改版後就沒有人說得清楚他同意過什麼。
-- 是否需要重新同意＝該使用者最新一筆的 version 不等於目前版本（目前版本在應用程式設定）。

CREATE TABLE terms_consents (
    id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid         NOT NULL,
    terms_type  varchar(20)  NOT NULL,
    version     varchar(50)  NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT ck_terms_consents_type CHECK (terms_type IN ('DONOR')),
    CONSTRAINT fk_terms_consents_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_terms_consents_user_type
    ON terms_consents (user_id, terms_type, created_at DESC);
