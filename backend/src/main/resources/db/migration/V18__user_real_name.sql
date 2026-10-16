-- 使用者的真實姓名。
--
-- 與 display_name 是兩回事：display_name 是機構與孩子看到的名字（可以用暱稱），
-- name 則是用於捐贈收據與向衛福部申報的徵信名冊，不會出現在任何對外的回應裡。
--
-- 允許 NULL：既有帳號、機構成員與管理員都沒有這個資料，只有新註冊的認領者在
-- 註冊表單會被要求填寫（想匿名的人填「善心人士」）。不加 NOT NULL 約束，理由與
-- V17 相同——補不出合理的預設值，約束只會讓 migration 當場失敗。
ALTER TABLE users ADD COLUMN name varchar(100);
