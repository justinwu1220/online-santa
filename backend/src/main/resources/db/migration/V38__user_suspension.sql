-- 凍結／停權處置 階段 3（認領者違規）：認領者停權（受限登入）與管理員釋出認領。
--
-- 停權不是 users.disabled（那是「完全拒絕登入」，沒有任何入口能設定，保留作極端情況的後路）：
-- 被停權的認領者仍可登入並看到停權通知，但所有寫入動作被擋（認領、傳訊、改資料、寄送…）。

ALTER TABLE users ADD COLUMN suspended_at timestamptz;
ALTER TABLE users ADD COLUMN suspended_reason text;
ALTER TABLE users ADD COLUMN suspended_by uuid;

ALTER TABLE users ADD CONSTRAINT ck_users_suspended
    CHECK ((suspended_at IS NULL AND suspended_reason IS NULL) OR (suspended_at IS NOT NULL AND suspended_reason IS NOT NULL));

-- 認領歷程：管理員釋出一筆認領（例如認領者違規）
ALTER TABLE claim_events DROP CONSTRAINT ck_claim_events_type;
ALTER TABLE claim_events ADD CONSTRAINT ck_claim_events_type
    CHECK (event_type IN ('CLAIMED', 'SHIPPED', 'RECEIVED', 'COMPLETED',
                          'RELEASED_MANUAL', 'RELEASED_AUTO', 'RELEASED_ADMIN', 'CANCELLED',
                          'FEEDBACK_UPLOADED'));
