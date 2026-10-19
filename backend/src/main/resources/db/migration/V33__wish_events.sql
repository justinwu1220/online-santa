-- 機構修改已提交的願望並重新送審：願望的歷程紀錄（append-only，比照 claim_events）。
-- 管理員重審時看得到過去的送審、核准、退回、凍結與修改；snapshot 是當時的內容（JSON 文字），
-- 用來比對「修改了什麼」與判斷下架修改後能不能直接恢復上架。

CREATE TABLE wish_events (
    id             bigint        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    wish_id        uuid          NOT NULL,
    event_type     varchar(30)   NOT NULL,
    actor_user_id  uuid,                       -- 系統動作為 NULL
    note           text,
    snapshot       text,
    created_at     timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT ck_wish_events_type
        CHECK (event_type IN ('SUBMITTED', 'RESUBMITTED', 'APPROVED', 'REJECTED', 'WITHDRAWN',
                              'TAKEN_DOWN_FOR_EDIT', 'RESTORED',
                              'FROZEN', 'UNFROZEN', 'FREEZE_LIFTED_BY_RESUBMIT',
                              'RECALLED_FOR_EDIT')),
    CONSTRAINT fk_wish_events_wish
        FOREIGN KEY (wish_id) REFERENCES wishes (id) ON DELETE CASCADE,
    CONSTRAINT fk_wish_events_actor
        FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_wish_events_wish ON wish_events (wish_id, created_at);
