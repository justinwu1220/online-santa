-- =============================================================================
-- 三聯單的用途更正：紅聯給捐贈人，白聯與黃聯由協會（康乃心）留存。
--   先前誤成「白聯給捐贈人、黃紅聯給協會」。檔案改為：
--     red_object          紅聯（第 3 頁，1 頁）→ 寄給勾選要收據的捐贈人
--     white_yellow_object 白聯＋黃聯（第 1–2 頁，2 頁）→ 協會留存，只在管理員匯出提供，不寄信
--   欄位直接改名；舊的 PDF 內容是反的，所以已產生的一律改回「待產生」，之後預覽或匯出時依快照重新產生。
-- =============================================================================
UPDATE donation_receipts
   SET pdf_status = 'PENDING', pdf_generated_at = NULL, pdf_expires_at = NULL,
       white_object = NULL, yellow_red_object = NULL
 WHERE pdf_status = 'READY';

ALTER TABLE donation_receipts RENAME COLUMN white_object TO red_object;
ALTER TABLE donation_receipts RENAME COLUMN yellow_red_object TO white_yellow_object;
ALTER TABLE donation_receipts RENAME COLUMN white_sent_at TO red_sent_at;
ALTER TABLE donation_receipts RENAME COLUMN white_send_error TO red_send_error;
