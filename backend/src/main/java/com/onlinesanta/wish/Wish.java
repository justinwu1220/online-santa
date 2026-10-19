package com.onlinesanta.wish;

import java.time.Instant;

import com.onlinesanta.common.BaseEntity;
import com.onlinesanta.common.exception.BusinessRuleException;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 孩童的願望。
 *
 * <p><strong>隱私：</strong>這裡沒有姓名、生日、住址或照片欄位，只有暱稱、年齡區間
 * 與興趣描述。資料庫 schema 同樣不存在那些欄位——沒有地方可存，就沒有外洩的可能。
 */
@Entity
@Table(name = "wishes")
public class Wish extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    /** 草稿可以先空著（只需要標題），提交審核時必填。 */
    @Column(name = "child_alias", length = 50)
    private String childAlias;

    /** 草稿可以先空著，提交審核時必填。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "age_range", length = 20)
    private AgeRange ageRange;

    @Column(length = 500)
    private String interests;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    /** 頭貼檔名，建立時挑一次（見 {@link WishAvatar}）。 */
    @Column(nullable = false, length = 30)
    private String avatar;

    /** 1018 新版起機構表單已無此欄位，舊資料保留；新願望為 null。 */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private WishCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_range", length = 20)
    private PriceRange priceRange;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WishStatus status;

    /**
     * 樂觀鎖版本號。M2 的認領流程會以一句原子條件 UPDATE 遞增此欄位，
     * 藉此在搶領尖峰下防止同一願望被重複認領。
     */
    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "published_at")
    private Instant publishedAt;

    /** 管理員退回的原因；重新提交或核准後仍保留，供機構對照。 */
    @Column(name = "review_note", columnDefinition = "text")
    private String reviewNote;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by")
    private java.util.UUID reviewedBy;

    protected Wish() {
        // JPA
    }

    private Wish(Organization organization, String childAlias, AgeRange ageRange, String interests,
                 String title, String description, WishCategory category, PriceRange priceRange) {
        this.organization = organization;
        this.childAlias = childAlias;
        this.ageRange = ageRange;
        this.interests = interests;
        this.title = title;
        this.description = description;
        this.category = category;
        this.priceRange = priceRange;
        this.status = WishStatus.DRAFT;
        this.avatar = WishAvatar.pick(ageRange);
    }

    /**
     * 新建的願望一律為草稿，讓機構在公開前有檢查隱私用字的機會。
     *
     * <p>這裡刻意只要求 {@code canDraftWishes()} 而非 {@code canPublishWishes()}：
     * 待審核的機構就能先把內容準備好，核准後一鍵上架。草稿不公開，把關留在
     * {@link #publish()}。
     */
    public static Wish draft(Organization organization, String childAlias, AgeRange ageRange,
                             String interests, String title, String description,
                             WishCategory category, PriceRange priceRange) {
        if (!organization.canDraftWishes()) {
            throw new BusinessRuleException("ORGANIZATION_SUSPENDED",
                    "機構已停權，無法建立願望，請與平台管理員聯繫");
        }
        return new Wish(organization, childAlias, ageRange, interests,
                title, description, category, priceRange);
    }

    public void updateContent(String childAlias, AgeRange ageRange, String interests,
                              String title, String description,
                              WishCategory category, PriceRange priceRange) {
        requireEditable("修改");
        this.avatar = WishAvatar.afterEdit(this.avatar, this.ageRange, ageRange);
        this.childAlias = childAlias;
        this.ageRange = ageRange;
        this.interests = interests;
        this.title = title;
        this.description = description;
        this.category = category;
        this.priceRange = priceRange;
    }

    /**
     * 機構提交審核：草稿 → 審核中。機構需已通過審核；內容是否齊全（例如信件照片）由呼叫端檢查。
     */
    public void submitForReview() {
        if (status != WishStatus.DRAFT) {
            throw new BusinessRuleException("WISH_NOT_SUBMITTABLE",
                    "只有草稿能提交審核，目前狀態為 " + status);
        }
        if (!organization.canPublishWishes()) {
            throw new BusinessRuleException("ORGANIZATION_NOT_APPROVED",
                    "機構資料審核通過後才能提交願望審核，目前願望只能存成草稿");
        }
        // 草稿只需要標題；提交審核才要求全部填好
        if (childAlias == null || childAlias.isBlank() || ageRange == null) {
            throw new BusinessRuleException("WISH_INCOMPLETE",
                    "請先填寫孩子的暱稱與年齡區間，再提交審核");
        }
        this.status = WishStatus.PENDING_REVIEW;
    }

    /** 機構撤回：審核中 → 草稿，可以再編輯。 */
    public void withdraw() {
        if (status != WishStatus.PENDING_REVIEW) {
            throw new BusinessRuleException("WISH_NOT_WITHDRAWABLE",
                    "只有審核中的願望能撤回，目前狀態為 " + status);
        }
        this.status = WishStatus.DRAFT;
    }

    /** 管理員核准：審核中 → 可認領，公開在願望牆。 */
    public void approve(java.util.UUID reviewerId) {
        requirePendingReview("核准");
        publish();
        this.reviewNote = null;
        this.reviewedBy = reviewerId;
        this.reviewedAt = Instant.now();
    }

    /** 管理員退回：審核中 → 草稿，並留下原因給機構。 */
    public void reject(java.util.UUID reviewerId, String reason) {
        requirePendingReview("退回");
        this.status = WishStatus.DRAFT;
        this.reviewNote = reason;
        this.reviewedBy = reviewerId;
        this.reviewedAt = Instant.now();
    }

    private void requirePendingReview(String action) {
        if (status != WishStatus.PENDING_REVIEW) {
            throw new BusinessRuleException("WISH_NOT_PENDING_REVIEW",
                    "只有審核中的願望能" + action + "，目前狀態為 " + status);
        }
    }

    /**
     * 轉為可認領並記下發布時間（只記第一次）。核准流程的底層轉換；
     * 公開，是因為不少測試夾具要直接建出「已上架」的願望。
     */
    public void publish() {
        if (status != WishStatus.DRAFT && status != WishStatus.PENDING_REVIEW) {
            throw new BusinessRuleException("WISH_NOT_PUBLISHABLE",
                    "只有草稿或審核中的願望能上架，目前狀態為 " + status);
        }
        if (!organization.canPublishWishes()) {
            throw new BusinessRuleException("ORGANIZATION_NOT_APPROVED",
                    "機構尚未通過審核，還不能上架願望");
        }
        this.status = WishStatus.AVAILABLE;
        if (publishedAt == null) {
            this.publishedAt = Instant.now();
        }
    }

    public boolean isDeletable() {
        return status == WishStatus.DRAFT;
    }

    /**
     * 公開端點是否應顯示此願望：草稿只有機構自己看得到；機構被停權後，
     * 它名下的願望也一併從公開曝光下架——停權是資安事件應變手冊的第一步，
     * 若願望還留在牆上或詳情頁看得到，這一步就沒有真的生效。
     */
    public boolean isPubliclyVisible() {
        return status != WishStatus.DRAFT && status != WishStatus.PENDING_REVIEW
                && organization.getStatus() == OrganizationStatus.APPROVED;
    }

    private void requireEditable(String action) {
        if (!status.isEditable()) {
            throw new BusinessRuleException("WISH_NOT_EDITABLE",
                    "願望已進入認領流程，無法" + action + "，目前狀態為 " + status);
        }
    }

    public Organization getOrganization() {
        return organization;
    }

    public String getChildAlias() {
        return childAlias;
    }

    public AgeRange getAgeRange() {
        return ageRange;
    }

    public String getInterests() {
        return interests;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getAvatar() {
        return avatar;
    }

    public WishCategory getCategory() {
        return category;
    }

    public PriceRange getPriceRange() {
        return priceRange;
    }

    public WishStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
