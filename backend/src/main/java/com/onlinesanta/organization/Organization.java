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

    /** 自動收回的寬限天數只能從這四個選項挑（設計稿 15.7），預設 3 天。 */
    public static final java.util.Set<Integer> GRACE_DAY_CHOICES = java.util.Set.of(1, 3, 5, 7);

    @Column(nullable = false, length = 120)
    private String name;

    /** 承辦人姓名。是機構的屬性而非某位使用者的——一家機構日後可能有多位成員 */
    @Column(name = "contact_person", length = 100)
    private String contactPerson;

    @Column(name = "contact_email", nullable = false, length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", length = 40)
    private String contactPhone;

    /**
     * 行動電話。註冊與更新時必填（DTO 層的 @NotBlank 把關），contactPhone 則是選填。
     * 這裡刻意不擋：既有機構有完全沒填行動電話的，大量測試資料也用 null 建機構。
     * 資料庫同樣沒有 CHECK，原因見 V17 的註解
     */
    @Column(name = "mobile_phone", length = 40)
    private String mobilePhone;

    @Column(length = 255)
    private String address;

    @Column(name = "preferred_delivery_time", length = 255)
    private String preferredDeliveryTime;

    @Column(columnDefinition = "text")
    private String description;

    /** 機構官方網站（選填，完整網址）。願望詳細頁的「前往機構官方網站」按鈕。 */
    @Column(length = 255)
    private String website;

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

    private Organization(String name, String contactPerson, String contactEmail,
                         String contactPhone, String mobilePhone, String address,
                         String preferredDeliveryTime, String description) {
        this.name = name;
        this.contactPerson = contactPerson;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.mobilePhone = mobilePhone;
        this.address = address;
        this.preferredDeliveryTime = preferredDeliveryTime;
        this.description = description;
        this.status = OrganizationStatus.PENDING;
        this.releasePolicy = ReleasePolicy.MANUAL;
    }

    /** 自助註冊：一律從 PENDING 起算，須經管理員核准才能上架願望。 */
    public static Organization register(String name, String contactPerson, String contactEmail,
                                        String contactPhone, String mobilePhone, String address,
                                        String preferredDeliveryTime, String description) {
        return new Organization(name, contactPerson, contactEmail, contactPhone, mobilePhone,
                address, preferredDeliveryTime, description);
    }

    /**
     * 沒有行動電話與方便收件時間時的相容建構子——大量既有測試用這個 6 參數版本當測試
     * 資料快速建機構，不用跟著這些新欄位一一改。
     */
    public static Organization register(String name, String contactPerson, String contactEmail,
                                        String contactPhone, String address, String description) {
        return register(name, contactPerson, contactEmail, contactPhone, null, address,
                null, description);
    }

    public void updateProfile(String name, String contactPerson, String contactEmail,
                              String contactPhone, String mobilePhone, String address,
                              String preferredDeliveryTime, String description) {
        this.name = name;
        this.contactPerson = contactPerson;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.mobilePhone = mobilePhone;
        this.address = address;
        this.preferredDeliveryTime = preferredDeliveryTime;
        this.description = description;
    }

    public void updateWebsite(String website) {
        this.website = website == null || website.isBlank() ? null : website.trim();
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
            if (afterDays == null || !GRACE_DAY_CHOICES.contains(afterDays)) {
                throw new BusinessRuleException("INVALID_RELEASE_POLICY",
                        "選擇自動收回時，寬限天數必須是 1、3、5 或 7 天");
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

    /**
     * 停權：資安事件應變手冊（PRIVACY.md）的第一步。停權後機構名下的願望立刻從
     * 公開曝光下架（見 {@code Wish.isPubliclyVisible()}），但進行中的認領不受影響
     * ——已經認領的送禮流程繼續走完，停權處理的是「不再讓更多人接觸」，不是追溯。
     */
    public void suspend(UUID reviewerId, String note) {
        this.status = OrganizationStatus.SUSPENDED;
        this.reviewedBy = reviewerId;
        this.reviewNote = note;
        this.reviewedAt = Instant.now();
    }

    /** 復權：停權後恢復為 APPROVED，願望重新出現在公開曝光。 */
    public void reactivate(UUID reviewerId, String note) {
        this.status = OrganizationStatus.APPROVED;
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

    public boolean canDraftWishes() {
        return status.canDraftWishes();
    }

    public String getName() {
        return name;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getContactPerson() {
        return contactPerson;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public String getMobilePhone() {
        return mobilePhone;
    }

    public String getAddress() {
        return address;
    }

    public String getPreferredDeliveryTime() {
        return preferredDeliveryTime;
    }

    public String getDescription() {
        return description;
    }

    public String getWebsite() {
        return website;
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
