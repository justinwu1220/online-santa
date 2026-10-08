-- 機構文件（以及其他附件用途）現在可以記錄使用者上傳時的原始檔名，供監控
-- 中心與機構後台顯示。NULL 代表這支功能上線前就已存在的舊附件，沒有這項
-- 資訊——前端遇到 NULL 時退回原本的通用文字，不強制補值。
ALTER TABLE attachments ADD COLUMN original_filename VARCHAR(255);
