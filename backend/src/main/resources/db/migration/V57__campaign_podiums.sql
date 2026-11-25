-- 每一屆活動的前三名（榮譽榜）。
--
-- 稱號是累計的成就、解鎖後永久持有，不隨檔期重置，所以不需要逐人的歷年快照；
-- 但「某一屆的前三名是誰」是那一屆的結果，要定住——日後認領資料被整理、
-- 或有人在下一屆超車，都不該改寫歷史。
--
-- 並列時同名次，所以一屆可能有三筆以上（例如兩個第 1 名、一個第 3 名）。
-- （檔期、使用者）唯一：重跑結案是更新同一列。
-- 不存顯示名稱——名字隨時可改，公開與否也由本人決定，要顯示時再讀目前的值。

CREATE TABLE campaign_podiums (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id uuid        NOT NULL REFERENCES campaigns (id),
    user_id     uuid        NOT NULL REFERENCES users (id),
    rank_no     integer     NOT NULL CHECK (rank_no BETWEEN 1 AND 3),
    delivered   integer     NOT NULL CHECK (delivered > 0),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_campaign_podium UNIQUE (campaign_id, user_id)
);
