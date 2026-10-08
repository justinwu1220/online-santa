-- 機構後台「新增願望」拿掉預估價格欄位，價格區間改成選填。
-- ck_wishes_price_range 這個 CHECK 不用動：CHECK (price_range IN (...)) 在
-- Postgres 裡遇到 NULL 本來就視為通過（三值邏輯），只有拿掉 NOT NULL 才會
-- 真的卡住空值。

ALTER TABLE wishes ALTER COLUMN price_range DROP NOT NULL;
