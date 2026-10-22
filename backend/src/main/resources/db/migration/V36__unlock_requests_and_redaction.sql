-- 凍結／停權處置 階段 1（個資外洩）：機構的「申請解除」、管理員改願望內容、歷程快照與對話訊息的遮蔽。

-- 機構對管理員提出的解除申請。情境一（解除願望凍結）與情境二（解除機構停權）共用。
-- payload 是機構修改後的內容（JSON 文字，願望為暱稱／年齡／興趣／說明），核准時才套用到線上內容。
CREATE TABLE unlock_requests (
    id             bigint        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    target_type    varchar(20)   NOT NULL,
    target_id      uuid          NOT NULL,
    status         varchar(20)   NOT NULL DEFAULT 'PENDING',
    message        text,
    payload        text,
    requested_by   uuid,
    created_at     timestamptz   NOT NULL DEFAULT now(),
    decided_by     uuid,
    decided_at     timestamptz,
    decision_note  text,

    CONSTRAINT ck_unlock_requests_target CHECK (target_type IN ('WISH', 'ORGANIZATION')),
    CONSTRAINT ck_unlock_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN'))
);

-- 同一個對象同時只能有一筆待審的申請
CREATE UNIQUE INDEX uq_unlock_requests_pending ON unlock_requests (target_type, target_id) WHERE status = 'PENDING';
CREATE INDEX idx_unlock_requests_target ON unlock_requests (target_type, target_id, created_at);

-- 願望歷程：新增管理員改內容與解除申請的事件；快照遮蔽時記下時間
ALTER TABLE wish_events DROP CONSTRAINT ck_wish_events_type;
ALTER TABLE wish_events ADD CONSTRAINT ck_wish_events_type
    CHECK (event_type IN ('SUBMITTED', 'RESUBMITTED', 'APPROVED', 'REJECTED', 'WITHDRAWN',
                          'TAKEN_DOWN_FOR_EDIT', 'RESTORED',
                          'FROZEN', 'UNFROZEN', 'FREEZE_LIFTED_BY_RESUBMIT',
                          'RECALLED_FOR_EDIT',
                          'ADMIN_EDITED', 'UNLOCK_REQUESTED', 'UNLOCK_APPROVED',
                          'UNLOCK_REJECTED', 'UNLOCK_WITHDRAWN'));
ALTER TABLE wish_events ADD COLUMN redacted_at timestamptz;

-- 對話訊息遮蔽：原文保留給稽核，雙方看到的是「已被管理員遮蔽」
ALTER TABLE messages ADD COLUMN redacted_at timestamptz;
ALTER TABLE messages ADD COLUMN redacted_by uuid;
ALTER TABLE messages ADD COLUMN redacted_reason text;
