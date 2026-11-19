package com.onlinesanta.closing;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.admin.AdminAuditAction;
import com.onlinesanta.admin.AdminAuditService;
import com.onlinesanta.closing.ClosingMailService.MailProgress;
import com.onlinesanta.closing.doc.ClosingStage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 結案寄信：預覽（GET，不寄）與寄出（POST，管理員確認後分段呼叫，直到沒有待寄）。
 */
@RestController
@RequestMapping("/api/admin/campaigns/{campaignId}/closing-mail")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理後台", description = "活動檔期與結案文件")
public class AdminClosingMailController {

    private final ClosingMailService mail;
    private final AdminAuditService audit;

    public AdminClosingMailController(ClosingMailService mail, AdminAuditService audit) {
        this.mail = mail;
        this.audit = audit;
    }

    @GetMapping("/receipts")
    @Operation(summary = "紅聯收據寄送預覽（不寄信）")
    public MailProgress previewReceipts(@PathVariable UUID campaignId) {
        return mail.previewReceipts(campaignId);
    }

    @PostMapping("/receipts/send")
    @Operation(summary = "寄出紅聯收據（分段；一次約 15 秒內，反覆呼叫到待寄為 0）")
    public MailProgress sendReceipts(@PathVariable UUID campaignId, @RequestParam(defaultValue = "false") boolean retryFailed) {
        MailProgress p = mail.sendReceipts(campaignId, retryFailed);
        audit.record(AdminAuditAction.SEND_RECEIPT_EMAILS, campaignId,
                "已寄 %d、失敗 %d、待寄 %d".formatted(p.sent(), p.failed(), p.pending()));
        return p;
    }

    @GetMapping("/org-forms")
    @Operation(summary = "機構確認表（05A＋05B）寄送預覽（不寄信）")
    public MailProgress previewOrgForms(@PathVariable UUID campaignId, @RequestParam ClosingStage stage) {
        return mail.previewOrgForms(campaignId, stage);
    }

    @PostMapping("/org-forms/send")
    @Operation(summary = "寄出機構確認表（分段）")
    public MailProgress sendOrgForms(@PathVariable UUID campaignId, @RequestParam ClosingStage stage,
                                     @RequestParam(defaultValue = "false") boolean retryFailed) {
        MailProgress p = mail.sendOrgForms(campaignId, stage, retryFailed);
        audit.record(AdminAuditAction.SEND_ORG_FORM_EMAILS, campaignId,
                "已寄 %d、失敗 %d、待寄 %d".formatted(p.sent(), p.failed(), p.pending()));
        return p;
    }
}
