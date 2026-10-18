-- 1018 新版：年齡固定六組、頭貼存庫、願望說明、禮物分類不再必填。
--
-- 1) 年齡：AGE_16_18 → AGE_16_PLUS（「16 歲以上」），其餘五組不變。
-- 2) 頭貼：wishes.avatar 存檔名。既有願望用和前端舊版 avatarFor() 相同的演算法
--    （FNV-1a 32 位元雜湊願望 id，再對該組張數取餘數）回填，所以升級後孩子看到的頭貼不會變。
-- 3) 願望說明：wishes.note，孩子口吻，20 字以內（長度由應用程式驗證，欄位留餘裕）。
-- 4) 禮物分類：機構表單拿掉這個欄位，改為可為 NULL；舊資料保留。

-- 1) 年齡
ALTER TABLE wishes DROP CONSTRAINT ck_wishes_age_range;
UPDATE wishes SET age_range = 'AGE_16_PLUS' WHERE age_range = 'AGE_16_18';
ALTER TABLE wishes ADD CONSTRAINT ck_wishes_age_range
    CHECK (age_range IN ('AGE_0_3', 'AGE_4_6', 'AGE_7_9',
                         'AGE_10_12', 'AGE_13_15', 'AGE_16_PLUS'));

-- 2) 頭貼
ALTER TABLE wishes ADD COLUMN avatar varchar(30);

CREATE FUNCTION pg_temp.fnv1a(text) RETURNS bigint AS $$
DECLARE
    h bigint := 2166136261;       -- 0x811c9dc5
    i int;
BEGIN
    FOR i IN 1..length($1) LOOP
        h := (h # ascii(substr($1, i, 1)));
        h := (h * 16777619) % 4294967296;
    END LOOP;
    RETURN h;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

UPDATE wishes SET avatar = CASE
    WHEN age_range = 'AGE_0_3' THEN
        (ARRAY['baby1.jpg', 'baby2.jpg', 'baby3.png', 'baby4.png'])[1 + (pg_temp.fnv1a(id::text) % 4)::int]
    ELSE
        (ARRAY['boy1.jpg', 'boy2.png', 'boy3.png', 'boy4.png', 'boy5.png',
               'girl1.png', 'girl2.jpg', 'girl3.png', 'girl4.jpg', 'girl5.jpg'])[1 + (pg_temp.fnv1a(id::text) % 10)::int]
    END;

ALTER TABLE wishes ALTER COLUMN avatar SET NOT NULL;

-- 3) 願望說明
ALTER TABLE wishes ADD COLUMN note varchar(60);

-- 4) 禮物分類
ALTER TABLE wishes ALTER COLUMN category DROP NOT NULL;
