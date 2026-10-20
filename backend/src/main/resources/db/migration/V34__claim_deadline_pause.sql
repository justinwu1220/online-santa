-- 管理員凍結已認領（還沒寄出）的願望時，認領者的寄送期限暫停計算；解除凍結時把暫停的時長加回期限。
-- deadline_paused_at 不為 null 代表正在暫停。

ALTER TABLE claims ADD COLUMN deadline_paused_at timestamptz;
