package com.onlinesanta.closing;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.onlinesanta.common.BaseEntity;
import com.onlinesanta.common.exception.BusinessRuleException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * 一張捐贈收據（一個完成的願望一張）。內容是開立當下的快照，之後不隨來源資料改變；
 * PDF 只是呈現，可隨時重新產生且內容相同。作廢重開保留原號與替代關係。
 */
@Entity
@Table(name = "donation_receipts")
public class DonationReceipt extends BaseEntity {

    /** 開立當下的快照內容，建立收據時一次給齊。 */
    public record Snapshot(
            boolean receiptRequested,
            String donorName,
            String donorIdEncrypted,
            String donorEmail,
            LocalDate donatedOn,
            LocalDate receivedOn,
            String giftDescription,
            int giftQuantity,
            String giftUnit,
            int giftValue,
            UUID organizationId,
            String organizationName,
            String organizationTaxId) {
    }

    @Column(name = "claim_id", nullable = false, updatable = false)
    private UUID claimId;

    @Column(name = "campaign_id", nullable = false, updatable = false)
    private UUID campaignId;

    @Column(name = "receipt_no", nullable = false, updatable = false, length = 40)
    private String receiptNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReceiptStatus status;

    @Column(name = "issued_on", nullable = false, updatable = false)
    private LocalDate issuedOn;

    @Column(name = "receipt_requested", nullable = false, updatable = false)
    private boolean receiptRequested;

    @Column(name = "donor_name", nullable = false, updatable = false, length = 100)
    private String donorName;

    @Column(name = "donor_id_encrypted", updatable = false, length = 255)
    private String donorIdEncrypted;

    @Column(name = "donor_email", updatable = false, length = 255)
    private String donorEmail;

    @Column(name = "donated_on", nullable = false, updatable = false)
    private LocalDate donatedOn;

    @Column(name = "received_on", updatable = false)
    private LocalDate receivedOn;

    @Column(name = "gift_description", nullable = false, updatable = false, length = 200)
    private String giftDescription;

    @Column(name = "gift_quantity", nullable = false, updatable = false)
    private int giftQuantity;

    @Column(name = "gift_unit", nullable = false, updatable = false, length = 10)
    private String giftUnit;

    @Column(name = "gift_value", nullable = false, updatable = false)
    private int giftValue;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "organization_name", nullable = false, updatable = false, length = 120)
    private String organizationName;

    @Column(name = "organization_tax_id", updatable = false, length = 8)
    private String organizationTaxId;

    @Column(name = "permit_no_used", length = 100)
    private String permitNoUsed;

    @Column(name = "activity_name_used", length = 200)
    private String activityNameUsed;

    @Column(name = "collection_start_used")
    private LocalDate collectionStartUsed;

    @Column(name = "collection_end_used")
    private LocalDate collectionEndUsed;

    @Enumerated(EnumType.STRING)
    @Column(name = "pdf_status", nullable = false, length = 20)
    private PdfStatus pdfStatus;

    @Column(name = "pdf_error", length = 500)
    private String pdfError;

    @Column(name = "white_object", length = 255)
    private String whiteObject;

    @Column(name = "yellow_red_object", length = 255)
    private String yellowRedObject;

    @Column(name = "pdf_generated_at")
    private Instant pdfGeneratedAt;

    @Column(name = "pdf_expires_at")
    private Instant pdfExpiresAt;

    @Column(name = "void_reason", length = 500)
    private String voidReason;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    @Column(name = "white_sent_at")
    private Instant whiteSentAt;

    @Column(name = "white_send_error", length = 500)
    private String whiteSendError;

    protected DonationReceipt() {
    }

    public DonationReceipt(UUID claimId, UUID campaignId, String receiptNo, LocalDate issuedOn, Snapshot s) {
        this.claimId = claimId;
        this.campaignId = campaignId;
        this.receiptNo = receiptNo;
        this.issuedOn = issuedOn;
        this.status = ReceiptStatus.ISSUED;
        this.receiptRequested = s.receiptRequested();
        this.donorName = s.donorName();
        this.donorIdEncrypted = s.donorIdEncrypted();
        this.donorEmail = s.donorEmail();
        this.donatedOn = s.donatedOn();
        this.receivedOn = s.receivedOn();
        this.giftDescription = s.giftDescription();
        this.giftQuantity = s.giftQuantity();
        this.giftUnit = s.giftUnit();
        this.giftValue = s.giftValue();
        this.organizationId = s.organizationId();
        this.organizationName = s.organizationName();
        this.organizationTaxId = s.organizationTaxId();
        this.pdfStatus = PdfStatus.PENDING;
    }

    // ------------------------------------------------------------ PDF 狀態

    /**
     * 第一次產生 PDF 時凍結檔期資訊；已凍結過就保持不變（之後重新產生內容一致）。
     */
    public void freezeCampaignInfo(String permitNo, String activityName, LocalDate collectionStart,
                                   LocalDate collectionEnd) {
        if (permitNoUsed == null) {
            this.permitNoUsed = permitNo;
            this.activityNameUsed = activityName;
            this.collectionStartUsed = collectionStart;
            this.collectionEndUsed = collectionEnd;
        }
    }

    public boolean hasFrozenCampaignInfo() {
        return permitNoUsed != null;
    }

    public void markPdfReady(String whiteObject, String yellowRedObject, Instant generatedAt, Instant expiresAt) {
        this.whiteObject = whiteObject;
        this.yellowRedObject = yellowRedObject;
        this.pdfGeneratedAt = generatedAt;
        this.pdfExpiresAt = expiresAt;
        this.pdfStatus = PdfStatus.READY;
        this.pdfError = null;
    }

    /** 版面放不下、字型缺字等：記下原因，列入檢查報告由人處理（不裁字、不漏列）。 */
    public void markPdfFailed(String error) {
        this.pdfStatus = PdfStatus.FAILED;
        this.pdfError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
    }

    /** 暫存的 PDF 已被清除（到期），資料仍在，下次需要時重新產生。 */
    public void markPdfExpired() {
        this.pdfStatus = PdfStatus.PENDING;
        this.whiteObject = null;
        this.yellowRedObject = null;
        this.pdfExpiresAt = null;
    }

    // ------------------------------------------------------------ 作廢與寄送

    public void voidWith(String reason, Instant at) {
        if (status == ReceiptStatus.VOIDED) {
            throw new BusinessRuleException("RECEIPT_ALREADY_VOIDED", "這張收據已經作廢");
        }
        this.status = ReceiptStatus.VOIDED;
        this.voidReason = reason;
        this.voidedAt = at;
    }

    public void replacedBy(UUID replacementId) {
        this.replacedById = replacementId;
    }

    public void markWhiteSent(Instant at) {
        this.whiteSentAt = at;
        this.whiteSendError = null;
    }

    public void markWhiteSendFailed(String error) {
        this.whiteSendError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
    }

    // ------------------------------------------------------------ getters

    public UUID getClaimId() {
        return claimId;
    }

    public UUID getCampaignId() {
        return campaignId;
    }

    public String getReceiptNo() {
        return receiptNo;
    }

    public ReceiptStatus getStatus() {
        return status;
    }

    public LocalDate getIssuedOn() {
        return issuedOn;
    }

    public boolean isReceiptRequested() {
        return receiptRequested;
    }

    public String getDonorName() {
        return donorName;
    }

    public String getDonorIdEncrypted() {
        return donorIdEncrypted;
    }

    public String getDonorEmail() {
        return donorEmail;
    }

    public LocalDate getDonatedOn() {
        return donatedOn;
    }

    public LocalDate getReceivedOn() {
        return receivedOn;
    }

    public String getGiftDescription() {
        return giftDescription;
    }

    public int getGiftQuantity() {
        return giftQuantity;
    }

    public String getGiftUnit() {
        return giftUnit;
    }

    public int getGiftValue() {
        return giftValue;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public String getOrganizationTaxId() {
        return organizationTaxId;
    }

    public String getPermitNoUsed() {
        return permitNoUsed;
    }

    public String getActivityNameUsed() {
        return activityNameUsed;
    }

    public LocalDate getCollectionStartUsed() {
        return collectionStartUsed;
    }

    public LocalDate getCollectionEndUsed() {
        return collectionEndUsed;
    }

    public PdfStatus getPdfStatus() {
        return pdfStatus;
    }

    public String getPdfError() {
        return pdfError;
    }

    public String getWhiteObject() {
        return whiteObject;
    }

    public String getYellowRedObject() {
        return yellowRedObject;
    }

    public Instant getPdfGeneratedAt() {
        return pdfGeneratedAt;
    }

    public Instant getPdfExpiresAt() {
        return pdfExpiresAt;
    }

    public String getVoidReason() {
        return voidReason;
    }

    public Instant getVoidedAt() {
        return voidedAt;
    }

    public UUID getReplacedById() {
        return replacedById;
    }

    public Instant getWhiteSentAt() {
        return whiteSentAt;
    }

    public String getWhiteSendError() {
        return whiteSendError;
    }
}
