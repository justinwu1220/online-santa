-- 機構新增兩個欄位：負責人姓名、扣繳單位統一編號（8 碼數字）。
-- 新申請與設定頁儲存時必填（DTO 驗證）；資料庫欄位刻意可空且不加 CHECK：
-- 既有機構沒有值，原因同 V16、V17。
ALTER TABLE organizations ADD COLUMN responsible_person varchar(100);
ALTER TABLE organizations ADD COLUMN withholding_tax_id varchar(8);

COMMENT ON COLUMN organizations.responsible_person IS '負責人姓名（機構的負責人、代表人或管理人）';
COMMENT ON COLUMN organizations.withholding_tax_id IS '扣繳單位統一編號（8 碼數字）';
