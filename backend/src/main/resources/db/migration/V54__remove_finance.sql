-- =============================================================================
-- 移除現金／財務資料：本活動沒有現金捐款，結案文件的現金、利息、自籌、現金實支固定為 0，
-- 不再需要管理員手動填寫的每月財務資料（V52 建立）與檔期自籌欄位。
-- =============================================================================
DROP TABLE IF EXISTS campaign_finance;
ALTER TABLE campaigns DROP COLUMN IF EXISTS self_funded_amount;
ALTER TABLE campaigns DROP COLUMN IF EXISTS self_funded_note;
