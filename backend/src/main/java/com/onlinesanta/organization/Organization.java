package com.onlinesanta.organization;

import java.time.Instant;
import java.util.UUID;

import com.onlinesanta.common.BaseEntity;
import com.onlinesanta.common.exception.BusinessRuleException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** 兒童機構（賣家）。 */
@Entity
@Table(name = "organizations")
public class Organization extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "contact_email", nullable = false, length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", length = 40)
    private String contactPhone;

    @Column(length = 255)
    private String address;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrganizationStatus status;

    @Column(name = "review_note", columnDefinition = "text")
    private String reviewNote;

    /**
     * 審核者。刻意存成裸的 UUID 而非 {@code @ManyToOne User}：機構與使用者互相參照
     * （users.organization_id 指回這裡），做成雙向關聯會讓載入路徑變得難以預測，
     * 而這個欄位只在管理後台顯示，不需要導覽能力。
     */
    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "release_policy", nullable = false, length = 10)
    private ReleasePolicy releasePolicy;

    @Column(name = "release_after_days")
    private Integer releaseAfterDays;

    protected Organization() {
        // JPA
    }

    private Organization(String name, String contactEmail, String contactPhone,
                         String address, String description) {
        this.name = name;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.address = address;
        this.description = description;
        this.status = OrganizationStatus.PENDING;
        this.releasePolicy = ReleasePolicy.MANUAL;
    }

    /** 自助註冊：一律從 PENDING 起算，須經管理員核准才能上架願望。 */
    public static Organization register(String name, String contactEmail, String contactPhone,
                                        String address, String description) {
        return new Organization(name, contactEmail, contactPhone, address, description);
    }

    public void updateProfile(String name, String contactEmail, String contactPhone,
                              String address, String description) {
        this.name = name;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.address = address;
        this.description = description;
    }

    /**
     * 設定逾期釋回政策。
     *
     * <p>兩個欄位必須一致：AUTO 一定要有天數、MANUAL 一定不能有。資料庫的
     * ck_org_release_after_days 也擋了同樣的組合，這裡先擋是為了回傳可讀的錯誤訊息，
     * 而非讓使用者看到資料庫的約束違反。
     */
    public void updateReleasePolicy(ReleasePolicy policy, Integer afterDays) {
        if (policy == ReleasePolicy.AUTO) {
            if (afterDays == null || afterDays < 1 || afterDays > 60) {
                throw new BusinessRuleException("INVALID_RELEASE_POLICY",
                        "選擇自動釋回時，寬限天數必須介於 1 到 60 天");
            }
            this.releaseAfterDays = afterDays;
        } else {
            this.releaseAfterDays = null;
        }
        this.releasePolicy = policy;
    }

    public void approve(UUID reviewerId, String note) {
        this.status = OrganizationStatus.APPROVED;
        this.reviewedBy = reviewerId;
        this.reviewNote = note;
        this.reviewedAt = Instant.now();
    }

    public void reject(UUID reviewerId, String note) {
        this.status = OrganizationStatus.REJECTED;
        this.reviewedBy = reviewerId;
        this.reviewNote = note;
        this.reviewedAt = Instant.now();
    }

    /** 被退件的機構補件後可重新送審。 */
    public void resubmitForReview() {
        if (status != OrganizationStatus.REJECTED) {
            throw new BusinessRuleException("NOT_REJECTED", "只有被退件的機構需要重新送審");
        }
        this.status = OrganizationStatus.PENDING;
        this.reviewedBy = null;
        this.reviewedAt = null;
    }

    public boolean canPublishWishes() {
        return status.canPublishWishes();
    }

    public String getName() {
        return name;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public String getAddress() {
        return address;
    }

    public String getDescription() {
        return description;
    }

    public OrganizationStatus getStatus() {
        return status;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public ReleasePolicy getReleasePolicy() {
        return releasePolicy;
    }

    public Integer getReleaseAfterDays() {
        return releaseAfterDays;
    }
}
