package com.onlinesanta.closing;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.admin.AdminAuditAction;
import com.onlinesanta.admin.AdminAuditService;
import com.onlinesanta.closing.dto.ReceiptCheckView;
import com.onlinesanta.closing.dto.ReceiptDownloadView;
import com.onlinesanta.closing.dto.ReceiptView;
import com.onlinesanta.closing.dto.VoidReceiptRequest;
import com.onlinesanta.common.PageResponse;
import com.onlinesanta.common.exception.BusinessRuleException;
import com.onlinesanta.common.exception.ResourceNotFoundException;
import com.onlinesanta.storage.ObjectStorage;
import com.onlinesanta.storage.StorageBucket;
import com.onlinesanta.storage.StorageProperties;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** 管理員處理捐贈收據：清單、預覽與下載、作廢重開、收據檢查。 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理後台", description = "活動檔期與結案文件")
public class AdminReceiptController {

    private final DonationReceiptRepository receipts;
    private final DonationReceiptService receiptService;
    private final CampaignService campaignService;
    private final ObjectStorage storage;
    private final StorageProperties storageProperties;
    private final AdminAuditService audit;
    private final ReceiptCheckService receiptCheck;

    public AdminReceiptController(DonationReceiptRepository receipts, DonationReceiptService receiptService,
                                  CampaignService campaignService, ObjectStorage storage,
                                  StorageProperties storageProperties, AdminAuditService audit,
                                  ReceiptCheckService receiptCheck) {
        this.receiptCheck = receiptCheck;
        this.receipts = receipts;
        this.receiptService = receiptService;
        this.campaignService = campaignService;
        this.storage = storage;
        this.storageProperties = storageProperties;
        this.audit = audit;
    }

    @GetMapping("/receipts")
    @Operation(summary = "收據清單", description = "指定檔期；可依收據狀態、PDF 狀態篩選，q 搜尋編號、捐贈者、機構。不含證號")
    public PageResponse<ReceiptView> list(
            @RequestParam UUID campaignId,
            @RequestParam(required = false) ReceiptStatus status,
            @RequestParam(required = false) PdfStatus pdfStatus,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "receiptNo") Pageable pageable) {
        campaignService.get(campaignId);
        return PageResponse.of(
                receipts.findAll(ReceiptSpecifications.search(campaignId, status, pdfStatus, q), pageable),
                ReceiptView::from);
    }

    @GetMapping("/receipts/{id}/download-url")
    @Operation(summary = "取得收據 PDF 的短效網址（在瀏覽器分頁預覽，可另存）",
            description = "kind＝RED（紅聯，給捐贈人）或 WHITE_YELLOW（白黃聯，協會留存）；mode＝PREVIEW（分頁預覽，預設）或 DOWNLOAD（直接存檔）。"
                    + "PDF 含捐贈者證號，每次開啟都寫稽核。"
                    + "暫存過期會先重新產生；版面放不下或證號解不開等無法產生時回 409")
    public ReceiptDownloadView downloadUrl(@PathVariable UUID id, @RequestParam PdfKind kind,
                                           @RequestParam(defaultValue = "PREVIEW") ReceiptOpenMode mode) {
        DonationReceipt receipt = receipts.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("收據", id));
        String object = receiptService.ensurePdfObject(id, kind).orElseThrow(() -> new BusinessRuleException(
                "RECEIPT_PDF_NOT_READY",
                "這張收據的 PDF 無法產生：" + (receipt.getPdfError() == null ? "請查看收據狀態" : receipt.getPdfError())));
        audit.record(AdminAuditAction.VIEW_RECEIPT_DOCUMENT, receipt.getClaimId(),
                "%s收據 %s（%s）".formatted(mode == ReceiptOpenMode.PREVIEW ? "預覽" : "下載", receipt.getReceiptNo(),
                        kind == PdfKind.RED ? "紅聯" : "白黃聯"));
        String fileName = receipt.getReceiptNo() + (kind == PdfKind.RED ? "-red.pdf" : "-white-yellow.pdf");
        var ttl = storageProperties.downloadUrlTtl();
        return new ReceiptDownloadView(
                storage.createDownloadUrl(StorageBucket.PRIVATE, object, ttl, fileName,
                        mode == ReceiptOpenMode.PREVIEW), Instant.now().plus(ttl));
    }

    @PostMapping("/receipts/{id}/void")
    @Operation(summary = "作廢並重開收據",
            description = "舊號保留（狀態作廢、記原因與替代收據），新收據用目前的認領資料與新號碼。重開不了就整個回滾")
    public ReceiptView voidAndReissue(@PathVariable UUID id, @Valid @RequestBody VoidReceiptRequest request) {
        DonationReceipt old = receipts.findById(id).orElseThrow(() -> ResourceNotFoundException.of("收據", id));
        DonationReceipt replacement = receiptService.voidAndReissue(id, request.reason());
        audit.record(AdminAuditAction.VOID_RECEIPT, old.getClaimId(),
                "作廢收據 %s，重開為 %s。原因：%s".formatted(old.getReceiptNo(), replacement.getReceiptNo(),
                        request.reason().strip()));
        return ReceiptView.from(replacement);
    }

    @GetMapping("/campaigns/{campaignId}/receipt-check")
    @Operation(summary = "收據檢查",
            description = "完成的願望是否都有有效收據、編號是否連續、PDF 是否都能產生、證號與發票佐證是否齊全。只列差異，不改資料")
    public ReceiptCheckView receiptCheck(@PathVariable UUID campaignId) {
        return receiptCheck.check(campaignId);
    }
}
