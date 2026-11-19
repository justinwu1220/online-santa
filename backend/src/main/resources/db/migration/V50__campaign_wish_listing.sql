-- =============================================================================
-- 檔期成為營運主軸：
--   1. 願望上架期間（wish_list_start ~ wish_list_end）：機構只能在期間內「新增」願望。
--      結束上架日必須早於勸募結束日（上架完還要留時間認領、寄送、確認收到）。
--   2. 移除統計截止日 cutoff_date：從來沒有任何計算用到它，統計期間改由檔期的勸募開始日決定。
--   3. 勸募許可文號改成建立檔期時必填（應用程式層驗證，欄位仍可為 NULL 以相容舊資料），
--      收據開立後自動產生 PDF，不再有「待補許可文號」狀態。
-- =============================================================================

ALTER TABLE campaigns ADD COLUMN wish_list_start date;
ALTER TABLE campaigns ADD COLUMN wish_list_end date;

-- 既有檔期回填：上架期間＝整個勸募期間（結束日往前一天，符合「結束上架日早於勸募結束日」）
UPDATE campaigns
   SET wish_list_end = collection_end - 1,
       wish_list_start = LEAST(collection_start, collection_end - 1);

ALTER TABLE campaigns ALTER COLUMN wish_list_start SET NOT NULL;
ALTER TABLE campaigns ALTER COLUMN wish_list_end SET NOT NULL;
ALTER TABLE campaigns ADD CONSTRAINT ck_campaigns_wish_listing
    CHECK (wish_list_start <= wish_list_end AND wish_list_end < collection_end);

ALTER TABLE campaigns DROP COLUMN cutoff_date;

-- 找「今天仍在上架期間的進行中檔期」
CREATE INDEX idx_campaigns_wish_listing ON campaigns (wish_list_start, wish_list_end) WHERE status = 'ACTIVE';

-- 待補許可文號狀態不再存在：舊的列改成待產生，下次預覽或匯出時依檔期現況重新產生
ALTER TABLE donation_receipts DROP CONSTRAINT ck_donation_receipts_pdf_status;
UPDATE donation_receipts SET pdf_status = 'PENDING' WHERE pdf_status = 'PENDING_INFO';
ALTER TABLE donation_receipts ADD CONSTRAINT ck_donation_receipts_pdf_status
    CHECK (pdf_status IN ('PENDING', 'READY', 'FAILED'));
