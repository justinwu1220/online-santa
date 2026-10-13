-- 行動電話。與 contact_phone 併存，機構填其中一個就算有聯絡方式——小型機構常常只有
-- 承辦人的手機，大型機構只公布總機；逼他們兩個都填只會換到一個假號碼。
--
-- 刻意不加 CHECK (contact_phone IS NOT NULL OR mobile_phone IS NOT NULL)：
--   1. 這支功能之前就存在的機構裡有完全沒填電話的，加了約束這支 migration 當場失敗；
--   2. 後端測試大量以 Organization.register(..., null, null, ...) 建測試資料，
--      約束會把它們全部擋掉；
--   3. 下游讀取端全部容許空值——ClaimDetail 的電話區塊有 && 判斷、後台的 Row 會印「—」。
--      這點跟 ck_org_release_after_days 不同：那條有排程任務依賴兩欄一致，
--      少了約束會讓釋回行為無法定義，這條沒有任何這樣的消費者。
-- 「至少填一個」因此只在 DTO 層（@AtLeastOnePhone）把關，與 V16 的作法一致。
ALTER TABLE organizations ADD COLUMN mobile_phone varchar(40);

COMMENT ON COLUMN organizations.mobile_phone IS '行動電話；與 contact_phone 至少需填一個，由應用層把關';
