-- 使用者聯絡電話。
--
-- 選填：機構聯繫捐贈者主要仍透過站內訊息與 email，電話只是額外留給機構
-- 方便聯繫用的，不像機構自己的 contact_phone 那樣是寄送流程的必要資訊。
ALTER TABLE users ADD COLUMN phone varchar(40);

COMMENT ON COLUMN users.phone IS '使用者聯絡電話，於個人檔案頁自行維護，選填';
