-- 信件照片屏蔽：管理員凍結時可以勾選屏蔽，屏蔽期間認領者與公開頁都看不到信件照片
-- （機構與管理員仍看得到，管理員可以審核或刪除）。凍結本身（含機構、認領者停權連動）不影響信件顯示。
ALTER TABLE wishes ADD COLUMN letter_masked boolean NOT NULL DEFAULT false;

-- 機構在解除凍結申請裡附上的新信件照片（核准前不上牆，核准後升級成 WISH_LETTER）
ALTER TABLE attachments DROP CONSTRAINT ck_attachments_owner_type;
ALTER TABLE attachments ADD CONSTRAINT ck_attachments_owner_type
    CHECK (owner_type IN ('WISH_IMAGE', 'SHIPPING_PROOF', 'ORG_FEEDBACK',
                          'ORG_DOCUMENT', 'WISH_LETTER', 'RECEIPT_PROOF', 'ORG_PHOTO',
                          'WISH_LETTER_REVISION'));

-- 稽核：管理員刪除願望信件照片
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
                      'REDACT_MESSAGE', 'DELETE_WISH_LETTER'));
