-- =============================================================================
-- 結案文件匯出作業：把檔期的收據（之後還有各式結案文件）打包成一個壓縮檔供管理員下載。
--
-- 為什麼是「作業」而不是一個同步請求：幾百張收據的 PDF 要先補產、再串流打包，單次請求放不下
-- （Cloud Run 30 秒、Firebase Hosting 轉發約 60 秒）。所以拆成可重複呼叫的步驟：前端輪詢
-- advance，每次在時間預算內做一段，進度存在這張表；中途斷線或實例被回收，下次呼叫從資料庫狀態接續。
--
-- 壓縮檔放私有 bucket，到期（app.closing.retention-days，預設 30 天）自動清除，過期後重新匯出即可。
-- =============================================================================
CREATE TABLE closing_exports (
    id             uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id    uuid          NOT NULL,
    kind           varchar(20)   NOT NULL,
    -- PREPARING 補產 PDF 中｜READY 壓縮檔已就緒｜FAILED 失敗（原因在 error）｜EXPIRED 已過期清除
    status         varchar(20)   NOT NULL DEFAULT 'PREPARING',
    -- 進度：要打包的收據張數，以及 PDF 已就緒的張數（PREPARING 階段）
    total          integer       NOT NULL DEFAULT 0,
    prepared       integer       NOT NULL DEFAULT 0,
    object_name    varchar(255),
    size_bytes     bigint,
    -- 因為 PDF 無法產生（缺許可文號、版面放不下）而沒有收進壓縮檔的收據張數
    skipped        integer       NOT NULL DEFAULT 0,
    error          varchar(500),
    expires_at     timestamptz,
    created_by     uuid,
    created_at     timestamptz   NOT NULL DEFAULT now(),
    updated_at     timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT ck_closing_exports_kind CHECK (kind IN ('RECEIPTS')),
    CONSTRAINT ck_closing_exports_status CHECK (status IN ('PREPARING', 'READY', 'FAILED', 'EXPIRED')),
    CONSTRAINT fk_closing_exports_campaign
        FOREIGN KEY (campaign_id) REFERENCES campaigns (id) ON DELETE CASCADE,
    CONSTRAINT fk_closing_exports_created_by
        FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_closing_exports_campaign ON closing_exports (campaign_id, created_at DESC);
CREATE INDEX idx_closing_exports_expiry ON closing_exports (expires_at) WHERE status = 'READY';

CREATE TRIGGER trg_closing_exports_updated_at
    BEFORE UPDATE ON closing_exports
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
