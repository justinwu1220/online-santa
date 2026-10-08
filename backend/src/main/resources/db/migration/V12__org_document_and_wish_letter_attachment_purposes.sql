-- 機構申請文件（ORG_DOCUMENT）與願望信件照片（WISH_LETTER）兩種新附件用途，
-- CHECK 約束跟著放寬。owner_id 依用途而定：ORG_DOCUMENT 指向機構，
-- WISH_LETTER 指向願望。

ALTER TABLE attachments DROP CONSTRAINT ck_attachments_owner_type;

ALTER TABLE attachments ADD CONSTRAINT ck_attachments_owner_type
    CHECK (owner_type IN ('WISH_IMAGE', 'SHIPPING_PROOF', 'ORG_FEEDBACK',
                          'ORG_DOCUMENT', 'WISH_LETTER'));
