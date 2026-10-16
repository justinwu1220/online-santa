-- 捐贈者回報寄送時填寫的禮物資訊，讓機構收貨時能核對、管理員處理申訴時有依據。
--
-- 全部允許 NULL：既有已寄出的認領沒有這些資料，補不出合理的預設值，NOT NULL 會讓
-- migration 當場失敗（理由同 V17、V18）。「必填」只在 ShipRequest 這一層把關——
-- 新的回報一定帶齊，舊資料維持空白，下游顯示端要容許空值。
--
-- shipped_on 是捐贈者填的實際寄出日，與 shipped_at（系統記錄「回報寄出」的時間）
-- 是兩回事：人可能隔幾天才來回報。
ALTER TABLE claims
    ADD COLUMN gift_description varchar(200),
    ADD COLUMN gift_quantity    integer,
    ADD COLUMN gift_price       integer,
    ADD COLUMN shipped_on       date,
    ADD CONSTRAINT ck_claims_gift_quantity CHECK (gift_quantity IS NULL OR gift_quantity > 0),
    ADD CONSTRAINT ck_claims_gift_price CHECK (gift_price IS NULL OR gift_price >= 0);
