-- 寄送期限提醒信是否已寄出。
--
-- 只是「有沒有寄過」的記號，不是提醒本身的資料——排程每天都會重新掃一次快到期的
-- 認領，用這個欄位擋掉對同一筆認領重複寄信。
ALTER TABLE claims ADD COLUMN deadline_reminder_sent_at timestamptz;

COMMENT ON COLUMN claims.deadline_reminder_sent_at IS
    '寄送期限提醒信已寄出的時間，null 代表還沒寄過；供排程判斷是否需要再寄一次';
