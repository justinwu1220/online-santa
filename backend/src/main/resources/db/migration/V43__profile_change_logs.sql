-- =============================================================================
-- 個資異動紀錄：誰、什麼時候、把哪一個欄位、從什麼改成什麼。認領者與機構共用一張表。
--
-- 為什麼要有這張表：上線後若發生爭議（「我沒有改過地址」「這支電話不是我填的」），
-- 目前系統答不出來——認領者改個人檔案完全沒有紀錄，機構則只有在停權期間才留。
--
-- target_type + target_id 的判別式沿用 unlock_requests 與 suspension_records。
-- =============================================================================
CREATE TABLE profile_change_logs (
    id             bigint        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- target_id 刻意不加外鍵：型別是多型的（指不到兩張表），而且
    -- organization_revisions 的 ON DELETE CASCADE 正是要避免的——
    -- 「對象一刪、證據跟著消失」的紀錄沒有意義
    target_type    varchar(20)   NOT NULL,
    target_id      uuid          NOT NULL,

    -- JSON 陣列：[{"field":"phone","before":"0912-000-000","after":"0912-345-678"}]
    -- 存機器欄位名，中文標籤留在前端——標籤日後被改寫時，既有證據的意思不該跟著變
    changes        text          NOT NULL,

    -- 操作者。email 在寫入當下去正規化保存，不在讀取時 join 解析：
    -- 要的是「當時的身分」，而 linkFirebaseUid 的存在就是為了支援換信箱重新註冊，
    -- 讀取時解析等於每次換信箱就偷偷改寫歷史
    actor_user_id  uuid,
    actor_email    varchar(255),
    actor_role     varchar(20),
    source         varchar(20)   NOT NULL,

    -- 時間以資料庫為準（不用 JVM 的 Instant.now()）：多個執行個體時鐘偏移會產生
    -- 順序錯亂的列。注意 now() 是交易開始時間，所以排序一律要帶 id 當 tiebreak
    created_at     timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT ck_profile_change_logs_target
        CHECK (target_type IN ('USER', 'ORGANIZATION')),
    CONSTRAINT ck_profile_change_logs_source
        CHECK (source IN ('SELF_SERVICE', 'ADMIN', 'SYSTEM', 'LEGACY')),
    CONSTRAINT ck_profile_change_logs_actor_role
        CHECK (actor_role IS NULL OR actor_role IN ('DONOR', 'ORG_MEMBER', 'ADMIN')),
    -- 只有系統自動改動與轉入的舊紀錄可以沒有操作者
    CONSTRAINT ck_profile_change_logs_actor
        CHECK (source IN ('SYSTEM', 'LEGACY') OR actor_user_id IS NOT NULL),
    -- 比照證據的要求：操作者不可被刪到讓紀錄指不出人
    CONSTRAINT fk_profile_change_logs_actor
        FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE RESTRICT
);

-- 四個索引各對應一種查法。
-- 「列出全部」的預設檢視與日期區間；id 收尾讓同一交易的兩筆有穩定順序，分頁才不會跳筆
CREATE INDEX idx_profile_change_logs_recent
    ON profile_change_logs (created_at DESC, id DESC);
-- 「這個人／這間機構的資料被改了什麼」
CREATE INDEX idx_profile_change_logs_target
    ON profile_change_logs (target_type, target_id, created_at DESC);
-- 「只看認領者」或「只看機構」。不能靠上面那個索引：只給 target_type 時
-- 必須掃完所有 target_id 才排得出 created_at
CREATE INDEX idx_profile_change_logs_type
    ON profile_change_logs (target_type, created_at DESC);
-- 「這位操作者改過誰的資料」——爭議處理最需要的反查
CREATE INDEX idx_profile_change_logs_actor
    ON profile_change_logs (actor_user_id, created_at DESC);

-- -----------------------------------------------------------------------------
-- 資料庫層的 append-only。
--
-- JPA 的 updatable=false 只擋得住應用程式這條路，而這份紀錄的價值全在「這些列沒被
-- 動過」，所以在資料庫擋。注意這擋得住應用程式與一般 SQL，擋不住有 superuser 權限
-- 的人（可以 DISABLE TRIGGER 後改完再開回來）——append-only 不等於防竄改。
--
-- 日後若因個資法的刪除請求必須塗銷，由新的 migration 明確
-- ALTER TABLE profile_change_logs DISABLE TRIGGER trg_profile_change_logs_append_only
-- 再改，讓這件事留在版本紀錄裡。
-- -----------------------------------------------------------------------------
CREATE FUNCTION profile_change_logs_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '個資異動紀錄只能新增，不能 %', TG_OP;
END;
$$;

CREATE TRIGGER trg_profile_change_logs_append_only
    BEFORE UPDATE OR DELETE OR TRUNCATE ON profile_change_logs
    FOR EACH STATEMENT EXECUTE FUNCTION profile_change_logs_append_only();

-- -----------------------------------------------------------------------------
-- 轉入既有的 organization_revisions。
--
-- 不「從零開始」的理由：管理員拿停權抽屜與新的查閱頁對照時，會看到一邊有、一邊沒有。
-- 在爭議裡「我們兩份紀錄對不起來」比「只有一份」更糟。
--
-- 形狀要轉換：{"field":{"before":..,"after":..}} → [{"field":..,"before":..,"after":..}]
-- ORDER BY e.key 取代原本 LinkedHashMap 的欄位順序（結果穩定，但不是原始順序）。
-- actor_role 斷定為 ORG_MEMBER：recordRevision 只有 updateMine 一條路徑進得來，
-- 而那條路徑在 requireOrganizationId() 後面。
-- source 用 LEGACY 而不是 SELF_SERVICE：這些列是「重建」出來的，證據紀錄該說實話。
-- -----------------------------------------------------------------------------
INSERT INTO profile_change_logs
    (target_type, target_id, changes, actor_user_id, actor_email, actor_role, source, created_at)
SELECT 'ORGANIZATION',
       r.organization_id,
       (SELECT jsonb_agg(jsonb_build_object(
                   'field',  e.key,
                   'before', coalesce(e.value ->> 'before', ''),
                   'after',  coalesce(e.value ->> 'after',  ''))
                 ORDER BY e.key)
          FROM jsonb_each(r.changes::jsonb) AS e)::text,
       r.created_by,
       u.email,
       CASE WHEN r.created_by IS NULL THEN NULL ELSE 'ORG_MEMBER' END,
       'LEGACY',
       r.created_at
  FROM organization_revisions r
  LEFT JOIN users u ON u.id = r.created_by
 WHERE r.changes IS NOT NULL AND r.changes <> '' AND r.changes <> '{}';

-- -----------------------------------------------------------------------------
-- 稽核：管理員查閱個資異動紀錄。
--
-- 這支端點會讓管理員看到任意他人的新舊真實姓名、電話、地址、統編，比已經會寫稽核的
-- VIEW_CLAIM_DETAIL 更敏感，所以查閱本身也要留痕。分成三個值是因為 targetType() 是
-- 靜態的 action→type 對應，同一個值不可能既是 USER 又是 ORGANIZATION。
--
-- 註：既有的 VIEW_SUSPENSION_RECORD 被對應成 USER，但機構停權的查閱也用它
-- （OrganizationSuspensionService），所以那些列的 target_type 是錯的。本次不動——
-- 為了讓報表好看而去 UPDATE 稽核紀錄，正是這張新表在反對的事。
-- -----------------------------------------------------------------------------
ALTER TABLE admin_audit_logs DROP CONSTRAINT ck_admin_audit_action;
ALTER TABLE admin_audit_logs ADD CONSTRAINT ck_admin_audit_action
    CHECK (action IN ('VIEW_CLAIM_DETAIL', 'VIEW_CLAIM_ATTACHMENTS', 'VIEW_CLAIM_MESSAGES',
                      'VIEW_CLAIM_RECEIPT',
                      'APPROVE_ORGANIZATION', 'REJECT_ORGANIZATION',
                      'SUSPEND_ORGANIZATION', 'REACTIVATE_ORGANIZATION',
                      'VIEW_ORGANIZATION_DOCUMENTS', 'VIEW_ORGANIZATION_PHOTO',
                      'DELETE_ATTACHMENT', 'DELETE_ORGANIZATION_DOCUMENT',
                      'RUN_RELEASE_SWEEP', 'RUN_ATTACHMENT_CLEANUP', 'RUN_DEADLINE_REMINDERS',
                      'APPROVE_WISH', 'REJECT_WISH',
                      'FREEZE_WISH', 'UNFREEZE_WISH', 'VIEW_WISH_ATTACHMENTS',
                      'SUSPEND_USER', 'REACTIVATE_USER', 'EDIT_WISH_CONTENT', 'RELEASE_CLAIM_BY_ADMIN',
                      'DECIDE_UNLOCK_REQUEST', 'NOTIFY_AFFECTED_DONORS', 'VIEW_SUSPENSION_RECORD',
                      'REDACT_MESSAGE', 'DELETE_WISH_LETTER',
                      'VIEW_USER_PROFILE_CHANGES', 'VIEW_ORGANIZATION_PROFILE_CHANGES',
                      'VIEW_PROFILE_CHANGE_LOG'));
