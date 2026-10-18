-- 1018 新版：機構官方網站（選填）與機構照片（ORG_PHOTO 附件用途）；
-- 另外禮物分類改為可為 NULL（機構表單保留這個欄位但不再必填；V23 已套用過，所以放在這支）。

ALTER TABLE wishes ALTER COLUMN category DROP NOT NULL;

ALTER TABLE organizations ADD COLUMN website varchar(255);

ALTER TABLE attachments DROP CONSTRAINT ck_attachments_owner_type;

ALTER TABLE attachments ADD CONSTRAINT ck_attachments_owner_type
    CHECK (owner_type IN ('WISH_IMAGE', 'SHIPPING_PROOF', 'ORG_FEEDBACK',
                          'ORG_DOCUMENT', 'WISH_LETTER', 'RECEIPT_PROOF', 'ORG_PHOTO'));
