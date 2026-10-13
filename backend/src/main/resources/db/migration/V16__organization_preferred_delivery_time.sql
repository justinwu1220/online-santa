-- 機構方便收件的時段說明，自由文字（例如「平日上午9:00-下午5:00」）。
-- 允許 NULL——這支功能上線前就存在的機構沒有填過；新申請與之後的設定頁
-- 編輯則由應用層（DTO 的 @NotBlank）要求必填。
ALTER TABLE organizations ADD COLUMN preferred_delivery_time varchar(255);
