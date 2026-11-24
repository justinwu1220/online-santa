package com.onlinesanta.closing;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.admin.AdminAuditAction;
import com.onlinesanta.admin.AdminAuditService;
import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.closing.doc.ClosingDocs;
import com.onlinesanta.closing.dto.ExportView;
import com.onlinesanta.closing.dto.ReceiptDownloadView;
import com.onlinesanta.common.TaiwanYear;
import com.onlinesanta.common.exception.BusinessRuleException;
import com.onlinesanta.storage.ObjectStorage;
import com.onlinesanta.storage.StorageBucket;
import com.onlinesanta.storage.StorageProperties;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 結案文件匯出：建立、推進、查進度、取得下載網址。
 *
 * <p>建立後前端反覆呼叫 {@code advance}（每次做一段，約 15 秒內），直到狀態是 READY 再取得下載網址。
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理後台", description = "活動檔期與結案文件")
public class AdminClosingExportController {

    private final ClosingExportService exports;
    private final CampaignService campaigns;
    private final ObjectStorage storage;
    private final StorageProperties storageProperties;
    private final CurrentUserService currentUser;
    private final AdminAuditService audit;

    public AdminClosingExportController(ClosingExportService exports, CampaignService campaigns,
                                        ObjectStorage storage, StorageProperties storageProperties,
                                        CurrentUserService currentUser, AdminAuditService audit) {
        this.exports = exports;
        this.campaigns = campaigns;
        this.storage = storage;
        this.storageProperties = storageProperties;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @PostMapping("/campaigns/{campaignId}/receipt-exports")
    @Operation(summary = "建立收據匯出（全部下載）",
            description = "把檔期的有效收據（紅聯、白黃聯）打包成一個壓縮檔。建立後請反覆呼叫 advance 推進")
    public ExportView create(@PathVariable UUID campaignId) {
        ClosingExport export = exports.start(campaignId, currentUser.requireAdmin().userId());
        audit.record(AdminAuditAction.CREATE_CLOSING_EXPORT, campaignId,
                "建立收據匯出，共 %d 張".formatted(export.getTotal()));
        return ExportView.from(export);
    }

    @PostMapping("/campaigns/{campaignId}/closing-packages")
    @Operation(summary = "建立結案文件包匯出",
            description = "成果備查與結案備查一律併報。把官方 Excel、核章 PDF、成果報告、05 表單、照表操作頁、"
                    + "收據（紅聯、白黃聯）與待補資料打包成一個壓縮檔。建立後請反覆呼叫 advance 推進")
    public ExportView createPackage(@PathVariable UUID campaignId) {
        ClosingExport export = exports.startPackage(campaignId, currentUser.requireAdmin().userId());
        audit.record(AdminAuditAction.CREATE_CLOSING_EXPORT, campaignId,
                "建立結案文件包（%s），收據共 %d 張".formatted(ClosingDocs.LABEL, export.getTotal()));
        return ExportView.from(export);
    }

    @PostMapping("/closing-exports/{id}/advance")
    @Operation(summary = "推進匯出作業一段",
            description = "補產還沒有的 PDF、並行讀回、串流打包；每次約 15 秒內回應。已完成的作業重複呼叫無害")
    public ExportView advance(@PathVariable UUID id) {
        return ExportView.from(exports.advance(id));
    }

    @GetMapping("/closing-exports/{id}")
    @Operation(summary = "匯出作業進度")
    public ExportView get(@PathVariable UUID id) {
        return ExportView.from(exports.get(id));
    }

    @GetMapping("/closing-exports/{id}/download-url")
    @Operation(summary = "取得壓縮檔的短效下載網址（直接存檔）",
            description = "壓縮檔含捐贈者證號，每次下載都寫稽核。尚未完成或已過期回 409")
    public ReceiptDownloadView downloadUrl(@PathVariable UUID id) {
        ClosingExport export = exports.get(id);
        if (export.getStatus() != ExportStatus.READY || export.getObjectName() == null) {
            throw new BusinessRuleException("EXPORT_NOT_READY", switch (export.getStatus()) {
                case PREPARING -> "匯出還在進行中，請等到完成再下載";
                case EXPIRED -> "這個匯出已過期被清除，請重新匯出";
                default -> "這個匯出失敗了：" + (export.getError() == null ? "請重新匯出" : export.getError());
            });
        }
        Campaign campaign = campaigns.get(export.getCampaignId());
        audit.record(AdminAuditAction.DOWNLOAD_CLOSING_EXPORT, export.getCampaignId(),
                (export.getKind() == ExportKind.CLOSING_PACKAGE ? "下載結案文件包（%s，檔期 %s，收據 %d 張）" : "下載收據壓縮檔（%s檔期 %s，%d 張）")
                        .formatted(export.getKind() == ExportKind.CLOSING_PACKAGE ? ClosingDocs.LABEL : "",
                                campaign.getName(), export.getTotal() - export.getSkipped()));
        String fileName = (export.getKind() == ExportKind.CLOSING_PACKAGE
                ? "closing-%s-%s.zip".formatted(campaign.getReceiptPrefix(), LocalDate.now(TaiwanYear.ZONE))
                : "receipts-%s-%s.zip".formatted(campaign.getReceiptPrefix(), LocalDate.now(TaiwanYear.ZONE)));
        var ttl = storageProperties.downloadUrlTtl();
        return new ReceiptDownloadView(
                storage.createDownloadUrl(StorageBucket.PRIVATE, export.getObjectName(), ttl, fileName, false),
                Instant.now().plus(ttl));
    }
}
