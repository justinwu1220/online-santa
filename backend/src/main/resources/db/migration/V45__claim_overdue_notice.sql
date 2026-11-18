-- 「寄送期限已過，請盡快處理」信是否已寄出（通知 4.4）。
--
-- 與 overdue_notified_at 是兩回事：後者是「機構自己決定」政策下，寄給機構的站內逾期通知，
-- 自動收回的認領永遠不會設它。這個欄位只記「寄給認領者的那封逾期信」，用來擋掉排程
-- 每天重新掃描時對同一筆認領重複寄信。
ALTER TABLE claims ADD COLUMN overdue_notice_sent_at timestamptz;

COMMENT ON COLUMN claims.overdue_notice_sent_at IS
    '寄給認領者的逾期提醒信已寄出的時間，null 代表還沒寄過；期限因解除凍結而順延時會清除';
