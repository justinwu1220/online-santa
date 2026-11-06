-- 移除管理員「遮蔽訊息」功能。已被遮蔽的訊息，原文直接改寫成遮蔽文字（原文永久消失），再刪掉欄位。
UPDATE messages SET body = '此訊息已被管理員遮蔽' WHERE redacted_at IS NOT NULL;

ALTER TABLE messages DROP COLUMN redacted_at;
ALTER TABLE messages DROP COLUMN redacted_by;
ALTER TABLE messages DROP COLUMN redacted_reason;
