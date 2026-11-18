-- 管理員每日待審摘要（通知 4.8）所需的兩樣東西。

-- 一、機構「本輪進入待審」的時間。
--
-- created_at 只是第一次申請的時間；補件後重新送審沒有任何時間戳，updated_at 又會因為任何
-- 資料修改而改變，兩者都不能拿來算「已等候多久」。所以送審（首次與重新送審）時另外記一筆。
ALTER TABLE organizations ADD COLUMN submitted_at timestamptz;

COMMENT ON COLUMN organizations.submitted_at IS
    '本輪送審（首次申請或補件後重新送審）的時間；摘要信用它算「最久已等候」。非待審狀態不必有值';

-- 既有的待審機構：只能以建立時間代替（重新送審的時間點已無從得知）
UPDATE organizations SET submitted_at = created_at WHERE status = 'PENDING';

-- 二、每日摘要的寄送紀錄：每位管理員每天（台灣日期）最多一封。
--
-- 排程重試或手動多按一次時，靠這張表擋掉重複寄信。寄失敗的會被刪掉紀錄，下次排程再補寄。
CREATE TABLE admin_digest_log (
    digest_date  date         NOT NULL,
    user_id      uuid         NOT NULL,
    sent_at      timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT pk_admin_digest_log PRIMARY KEY (digest_date, user_id),
    CONSTRAINT fk_admin_digest_log_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

COMMENT ON TABLE admin_digest_log IS '管理員每日待審摘要已寄出的紀錄（台灣日期 + 管理員），用來去重';
