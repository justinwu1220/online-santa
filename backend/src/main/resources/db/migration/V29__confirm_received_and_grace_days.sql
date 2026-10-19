-- 1018 新版：
-- 1) 機構「確認收到並完成」一步完成，要記「實際收到日期」（和按下確認的時間 received_at 分開記錄，
--    捐贈日期依實際收受事實認定）。
-- 2) 逾期自動收回改成「寄出期限（固定天數）＋寬限天數」：寬限天數在認領當下快照下來。
--    舊的認領沒有這個值，視為 0（維持原本「逾期就收回」的行為）。

ALTER TABLE claims ADD COLUMN received_on date;
ALTER TABLE claims ADD COLUMN release_grace_days integer;
