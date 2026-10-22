-- 凍結／停權處置 階段 2（機構違規）：停權當下保全的原始資訊，以及停權期間機構資料的修改紀錄。

-- 停權紀錄：停權當下的原始資訊快照（機構資料與證明文件清單，JSON 文字），append-only，
-- 解除停權只填 lifted_at。機構在停權期間可以改資料、補文件，快照保留原始樣貌供管理員比對。
CREATE TABLE suspension_records (
    id           bigint        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    target_type  varchar(20)   NOT NULL,
    target_id    uuid          NOT NULL,
    reason       text          NOT NULL,
    snapshot     text          NOT NULL,
    created_by   uuid,
    created_at   timestamptz   NOT NULL DEFAULT now(),
    lifted_at    timestamptz,
    lifted_by    uuid,

    CONSTRAINT ck_suspension_records_target CHECK (target_type IN ('ORGANIZATION', 'USER'))
);

CREATE INDEX idx_suspension_records_target ON suspension_records (target_type, target_id, created_at);

-- 機構資料的修改紀錄（append-only）：停權期間機構編輯資料，每次寫一筆「欄位：舊 → 新」，
-- 不覆蓋停權當下的快照。
CREATE TABLE organization_revisions (
    id               bigint        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id  uuid          NOT NULL,
    changes          text          NOT NULL,
    created_by       uuid,
    created_at       timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_organization_revisions_org
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE
);

CREATE INDEX idx_organization_revisions_org ON organization_revisions (organization_id, created_at);
