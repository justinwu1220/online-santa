package com.onlinesanta.wish;

import java.time.Instant;

import com.onlinesanta.common.BaseEntity;
import com.onlinesanta.common.exception.BusinessRuleException;
import com.onlinesanta.organization.Organization;

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

    @Column(name = "child_alias", nullable = false, length = 50)
    private String childAlias;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_range", nullable = false, length = 20)
    private AgeRange ageRange;

    @Column(length = 500)
    private String interests;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WishCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_range", nullable = false, length = 20)
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
        this.childAlias = childAlias;
        this.ageRange = ageRange;
        this.interests = interests;
        this.title = title;
        this.description = description;
        this.category = category;
        this.priceRange = priceRange;
    }

    /** 上架：草稿或已下架的願望都可以（重新）公開。 */
    public void publish() {
        if (status != WishStatus.DRAFT && status != WishStatus.ARCHIVED) {
            throw new BusinessRuleException("WISH_NOT_PUBLISHABLE",
                    "只有草稿或已下架的願望能上架，目前狀態為 " + status);
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

    /** 下架：已被認領的願望不能下架，否則捐贈者的認領會憑空消失。 */
    public void unpublish() {
        if (status != WishStatus.AVAILABLE) {
            throw new BusinessRuleException("WISH_NOT_UNPUBLISHABLE",
                    status == WishStatus.CLAIMED
                            ? "願望已被認領，無法下架"
                            : "只有上架中的願望能下架，目前狀態為 " + status);
        }
        this.status = WishStatus.ARCHIVED;
    }

    public boolean isDeletable() {
        return status == WishStatus.DRAFT;
    }

    /** 公開端點是否應顯示此願望：草稿只有機構自己看得到。 */
    public boolean isPubliclyVisible() {
        return status != WishStatus.DRAFT;
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
}
