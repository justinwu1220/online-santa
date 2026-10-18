-- 1018 新版：系統訊息與機構↔團隊對話。
--
-- 1) messages 以「願望」為單位也能掛訊息：
--    - wish_id（從 claims 回填），claim_id 與 sender_user_id 改為可為 NULL
--    - kind：USER＝認領者與機構的對話；SYSTEM＝系統通知（只給機構看，計入機構未讀）；
--      WELCOME＝認領當下機構對認領者的招呼（雙方都看得到，視為已讀）
--    - 系統訊息可能在沒有認領時發生（願望被核准／退回），所以 claim_id 可為 NULL
-- 2) claims.overdue_notified_at：「我自己決定」政策的機構，逾期時只通知一次
-- 3) team_messages：機構與平台團隊的對話，每個機構一條對話串，與認領無關

ALTER TABLE messages ADD COLUMN wish_id uuid;
UPDATE messages m SET wish_id = c.wish_id FROM claims c WHERE c.id = m.claim_id;
ALTER TABLE messages ALTER COLUMN wish_id SET NOT NULL;
ALTER TABLE messages ADD CONSTRAINT fk_messages_wish
    FOREIGN KEY (wish_id) REFERENCES wishes (id) ON DELETE CASCADE;

ALTER TABLE messages ALTER COLUMN claim_id DROP NOT NULL;
ALTER TABLE messages ALTER COLUMN sender_user_id DROP NOT NULL;

ALTER TABLE messages ADD COLUMN kind varchar(10) NOT NULL DEFAULT 'USER';
ALTER TABLE messages ADD CONSTRAINT ck_messages_kind CHECK (kind IN ('USER', 'SYSTEM', 'WELCOME'));
-- 使用者訊息一定有認領與寄件人；其餘種類沒有寄件人
ALTER TABLE messages ADD CONSTRAINT ck_messages_user_shape
    CHECK (kind <> 'USER' OR (claim_id IS NOT NULL AND sender_user_id IS NOT NULL));

CREATE INDEX idx_messages_wish ON messages (wish_id, created_at);
CREATE INDEX idx_messages_wish_unread ON messages (wish_id) WHERE read_at IS NULL;

ALTER TABLE claims ADD COLUMN overdue_notified_at timestamptz;

CREATE TABLE team_messages (
    id              bigint        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id uuid          NOT NULL,
    -- true＝團隊寄給機構；false＝機構成員寄給團隊
    from_team       boolean       NOT NULL,
    sender_user_id  uuid,
    body            varchar(2000) NOT NULL,
    read_at         timestamptz,
    created_at      timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_team_messages_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_team_messages_sender
        FOREIGN KEY (sender_user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_team_messages_org ON team_messages (organization_id, created_at);
CREATE INDEX idx_team_messages_unread ON team_messages (organization_id, from_team) WHERE read_at IS NULL;
