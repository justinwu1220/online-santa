-- 合作機構參與須知（第 7 版）也要記錄同意：放寬 terms_consents 的種類約束。
ALTER TABLE terms_consents DROP CONSTRAINT ck_terms_consents_type;

ALTER TABLE terms_consents ADD CONSTRAINT ck_terms_consents_type
    CHECK (terms_type IN ('DONOR', 'ORG'));
