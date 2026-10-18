-- 首次登入說明（「開始之前」彈窗）的完成時間。
--
-- NULL＝還沒看過。放在後端而不是瀏覽器的 localStorage：換裝置、換瀏覽器也不會重跳。
-- 本功能上線前就存在的帳號全部視為已看過（補成現在），只有之後新註冊的人會看到。

ALTER TABLE users ADD COLUMN onboarded_at timestamptz;

UPDATE users SET onboarded_at = now();
