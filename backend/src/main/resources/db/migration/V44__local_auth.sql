-- -----------------------------------------------------------------------------
-- 本機驗證：帳號、密碼、驗證碼與登入狀態改由本系統自己管理，不再依賴 Firebase Authentication。
--
-- 網站尚未對外開放，沒有需要搬移的 Firebase 帳號，因此直接移除 firebase_uid。
-- 已存在的測試帳號沒有密碼，之後用「忘記密碼」設定即可；它們的信箱視為已驗證
-- （原本由 Firebase 保證），避免管理員與機構成員被降成一般民眾。
-- -----------------------------------------------------------------------------

DROP INDEX IF EXISTS uq_users_firebase_uid;
ALTER TABLE users DROP COLUMN firebase_uid;

ALTER TABLE users
    ADD COLUMN password_hash       varchar(100),
    ADD COLUMN email_verified_at   timestamptz,
    ADD COLUMN failed_login_count  integer      NOT NULL DEFAULT 0,
    ADD COLUMN locked_until        timestamptz,
    ADD COLUMN password_changed_at timestamptz;

UPDATE users SET email_verified_at = created_at;

-- -----------------------------------------------------------------------------
-- auth_tokens — 一次性的信箱驗證碼（6 位數）與重設密碼 token
--
-- token_hash 不存明文：驗證碼存 HMAC（6 位數只有 100 萬種組合，單純的雜湊在資料庫外洩時
-- 可以瞬間離線破解），重設密碼 token 存 SHA-256。attempts 記錄驗證碼被猜錯的次數，
-- 達到上限即作廢，必須重寄新的。
-- -----------------------------------------------------------------------------
CREATE TABLE auth_tokens (
    id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid         NOT NULL,
    purpose     varchar(20)  NOT NULL,
    token_hash  varchar(128) NOT NULL,
    attempts    integer      NOT NULL DEFAULT 0,
    expires_at  timestamptz  NOT NULL,
    used_at     timestamptz,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT ck_auth_tokens_purpose
        CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD')),
    CONSTRAINT fk_auth_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_auth_tokens_user_purpose ON auth_tokens (user_id, purpose, created_at DESC);
CREATE INDEX idx_auth_tokens_hash ON auth_tokens (token_hash);

-- -----------------------------------------------------------------------------
-- refresh_tokens — 登入狀態（HttpOnly cookie 裡的 refresh token）
--
-- 每次換發都會輪替：舊的標記 revoked_at、發新的，兩者共用 family_id。已撤銷的 token
-- 又被拿來用，代表有人複製了 cookie，整個 family 一併撤銷。
-- -----------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid         NOT NULL,
    family_id   uuid         NOT NULL,
    token_hash  varchar(64)  NOT NULL,
    expires_at  timestamptz  NOT NULL,
    revoked_at  timestamptz,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_refresh_tokens_hash ON refresh_tokens (token_hash);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
