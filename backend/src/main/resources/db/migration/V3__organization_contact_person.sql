-- 承辦人姓名。
--
-- 原本這個欄位被存進 users.display_name，但那裡沒有任何地方讀得到——註冊時填了、
-- 之後就消失。而且語意也不對：display_name 是「這個人的名字」，承辦人是「這家機構
-- 找誰」，一家機構日後可能有多位成員，承辦人是機構的屬性而非某個使用者的。
--
-- 放在 contact_email / contact_phone 旁邊才是它該在的位置。
ALTER TABLE organizations ADD COLUMN contact_person varchar(100);

COMMENT ON COLUMN organizations.contact_person IS '承辦人姓名，管理員審核與捐贈者聯繫時使用';
