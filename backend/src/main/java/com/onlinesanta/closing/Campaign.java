package com.onlinesanta.closing;

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
 * 活動檔期：一次公益勸募活動的期間、許可文號與收據編號設定。結案文件的統計與收據都以它為準。
 *
 * <p>收據編號由這個實體配發（{@link #allocateReceiptNo()}）。呼叫端必須先用
 * {@code CampaignRepository.findByIdForUpdate} 鎖住這一列，否則兩個同時完成的願望會拿到同一個號碼。
 */
@Entity
@Table(name = "campaigns")
public class Campaign extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "activity_no", length = 30)
    private String activityNo;

    @Column(name = "permit_no", length = 100)
    private String permitNo;

    @Column(name = "approved_name", length = 200)
    private String approvedName;

    @Column(name = "collection_start", nullable = false)
    private LocalDate collectionStart;

    @Column(name = "collection_end", nullable = false)
    private LocalDate collectionEnd;

    @Column(name = "use_start", nullable = false)
    private LocalDate useStart;

    @Column(name = "use_end", nullable = false)
    private LocalDate useEnd;

    /** 願望上架期間：機構只能在這段期間內新增願望（修改、審核不受限）。結束日必須早於勸募結束日。 */
    @Column(name = "wish_list_start", nullable = false)
    private LocalDate wishListStart;

    @Column(name = "wish_list_end", nullable = false)
    private LocalDate wishListEnd;

    @Column(name = "receipt_prefix", nullable = false, length = 20)
    private String receiptPrefix;

    @Column(name = "next_receipt_seq", nullable = false)
    private int nextReceiptSeq;

    @Column(name = "receipts_issued", nullable = false)
    private int receiptsIssued;

    /**
     * 本次活動的合作機構（法人機構帳號，協助申請勸募許可文號的那一個）。每個檔期只會有一個。
     * 它自己服務對象收到的禮物走 05C（不是 05A／05B），也是成果報告「本會直接發放份數」的來源。
     */
    @Column(name = "association_org_id")
    private UUID associationOrgId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CampaignStatus status;

    protected Campaign() {
    }

    public Campaign(String name, LocalDate collectionStart, LocalDate collectionEnd,
                    LocalDate useStart, LocalDate useEnd, LocalDate wishListStart, LocalDate wishListEnd,
                    String receiptPrefix, int firstReceiptSeq) {
        this.name = name;
        this.collectionStart = collectionStart;
        this.collectionEnd = collectionEnd;
        this.useStart = useStart;
        this.useEnd = useEnd;
        this.wishListStart = wishListStart;
        this.wishListEnd = wishListEnd;
        this.receiptPrefix = receiptPrefix;
        this.nextReceiptSeq = firstReceiptSeq;
        this.status = CampaignStatus.ACTIVE;
        validatePeriods();
    }

    /** 修改設定。收據前綴與起始流水號在已配出任何收據之後不能再改（編號要穩定）。 */
    public void update(String name, String activityNo, String permitNo, String approvedName,
                       LocalDate collectionStart, LocalDate collectionEnd,
                       LocalDate useStart, LocalDate useEnd, LocalDate wishListStart, LocalDate wishListEnd,
                       String receiptPrefix, Integer firstReceiptSeq,
                       UUID associationOrgId, CampaignStatus status) {
        boolean numberingChanged = !this.receiptPrefix.equals(receiptPrefix)
                || (firstReceiptSeq != null && firstReceiptSeq != this.nextReceiptSeq);
        if (receiptsIssued > 0 && numberingChanged) {
            throw new BusinessRuleException("RECEIPT_NUMBERING_LOCKED",
                    "已經開立過收據，收據編號的前綴與起始號碼不能再修改");
        }
        this.name = name;
        this.activityNo = blankToNull(activityNo);
        this.permitNo = blankToNull(permitNo);
        this.approvedName = blankToNull(approvedName);
        this.collectionStart = collectionStart;
        this.collectionEnd = collectionEnd;
        this.useStart = useStart;
        this.useEnd = useEnd;
        this.wishListStart = wishListStart;
        this.wishListEnd = wishListEnd;
        this.receiptPrefix = receiptPrefix;
        if (receiptsIssued == 0 && firstReceiptSeq != null) {
            this.nextReceiptSeq = firstReceiptSeq;
        }
        this.associationOrgId = associationOrgId;
        this.status = status;
        validatePeriods();
    }

    private void validatePeriods() {
        if (collectionEnd.isBefore(collectionStart)) {
            throw new BusinessRuleException("INVALID_CAMPAIGN_PERIOD", "勸募結束日不能早於開始日");
        }
        if (useEnd.isBefore(useStart)) {
            throw new BusinessRuleException("INVALID_CAMPAIGN_PERIOD", "財物使用結束日不能早於開始日");
        }
        if (wishListEnd.isBefore(wishListStart)) {
            throw new BusinessRuleException("INVALID_WISH_LISTING_PERIOD", "願望結束上架日不能早於開始上架日");
        }
        if (!wishListEnd.isBefore(collectionEnd)) {
            throw new BusinessRuleException("INVALID_WISH_LISTING_PERIOD",
                    "願望結束上架日必須早於勸募結束日（上架結束後還要留時間認領、寄送與確認收到）");
        }
    }

    /**
     * 配一個收據編號，格式 {@code 前綴-六位流水號}（例如 OS2026-000042）。號碼只增不減。
     * 呼叫前必須已鎖住這一列。
     */
    public String allocateReceiptNo() {
        String no = "%s-%06d".formatted(receiptPrefix, nextReceiptSeq);
        nextReceiptSeq++;
        receiptsIssued++;
        return no;
    }

    /** 這一天是否開放機構新增願望：進行中、且落在上架期間內。 */
    public boolean listingOpenOn(LocalDate date) {
        return isActive() && !date.isBefore(wishListStart) && !date.isAfter(wishListEnd);
    }

    public boolean isActive() {
        return status == CampaignStatus.ACTIVE;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    public String getName() {
        return name;
    }

    public String getActivityNo() {
        return activityNo;
    }

    public String getPermitNo() {
        return permitNo;
    }

    public String getApprovedName() {
        return approvedName;
    }

    public LocalDate getCollectionStart() {
        return collectionStart;
    }

    public LocalDate getCollectionEnd() {
        return collectionEnd;
    }

    public LocalDate getUseStart() {
        return useStart;
    }

    public LocalDate getUseEnd() {
        return useEnd;
    }

    public LocalDate getWishListStart() {
        return wishListStart;
    }

    public LocalDate getWishListEnd() {
        return wishListEnd;
    }

    public String getReceiptPrefix() {
        return receiptPrefix;
    }

    public int getNextReceiptSeq() {
        return nextReceiptSeq;
    }

    public int getReceiptsIssued() {
        return receiptsIssued;
    }

    public UUID getAssociationOrgId() {
        return associationOrgId;
    }

    public CampaignStatus getStatus() {
        return status;
    }
}
