-- 捐贈收據不再收集收據寄送地址（活動不替捐贈者代寄紙本收據；電子收據寄到信箱）。
-- 一併刪除既有的地址資料，不再保留。
ALTER TABLE claim_receipts DROP COLUMN address;
