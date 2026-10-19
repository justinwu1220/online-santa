-- 設計稿 15.5：存成草稿只需要願望標題，提交審核才要求暱稱、年齡與信件照片全部填好。
-- 所以草稿的孩子暱稱與年齡區間可以先空著；提交審核時由應用程式檢查。

ALTER TABLE wishes ALTER COLUMN child_alias DROP NOT NULL;
ALTER TABLE wishes ALTER COLUMN age_range DROP NOT NULL;
