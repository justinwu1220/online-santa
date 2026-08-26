package com.onlinesanta.wish;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.common.exception.BusinessRuleException;
import com.onlinesanta.common.exception.ResourceNotFoundException;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationService;
import com.onlinesanta.wish.dto.WishRequest;

@Service
public class WishService {

    private final WishRepository wishes;
    private final OrganizationService organizations;
    private final CurrentUserService currentUser;

    public WishService(WishRepository wishes,
                       OrganizationService organizations,
                       CurrentUserService currentUser) {
        this.wishes = wishes;
        this.organizations = organizations;
        this.currentUser = currentUser;
    }

    // ---------------------------------------------------------------- 公開瀏覽

    /** 願望牆：只回上架中的願望，三個篩選條件皆為選填。 */
    @Transactional(readOnly = true)
    public Page<Wish> browse(WishCategory category, AgeRange ageRange,
                             PriceRange priceRange, Pageable pageable) {
        return wishes.search(WishStatus.AVAILABLE, category, ageRange, priceRange, pageable);
    }

    /** 公開的願望詳情。草稿不對外顯示，且不透露「存在但看不到」——一律回 404。 */
    @Transactional(readOnly = true)
    public Wish getPublicById(UUID id) {
        Wish wish = findById(id);
        if (!wish.isPubliclyVisible()) {
            throw ResourceNotFoundException.of("願望", id);
        }
        return wish;
    }

    // ---------------------------------------------------------------- 機構操作

    @Transactional
    public Wish create(WishRequest request) {
        Organization organization = organizations.getById(currentUser.requireOrganizationId());
        return wishes.save(Wish.draft(
                organization,
                request.childAlias(),
                request.ageRange(),
                request.interests(),
                request.title(),
                request.description(),
                request.category(),
                request.priceRange()));
    }

    @Transactional
    public Wish update(UUID id, WishRequest request) {
        Wish wish = findOwned(id);
        wish.updateContent(
                request.childAlias(),
                request.ageRange(),
                request.interests(),
                request.title(),
                request.description(),
                request.category(),
                request.priceRange());
        return wish;
    }

    @Transactional
    public Wish publish(UUID id) {
        Wish wish = findOwned(id);
        wish.publish();
        return wish;
    }

    @Transactional
    public Wish unpublish(UUID id) {
        Wish wish = findOwned(id);
        wish.unpublish();
        return wish;
    }

    /**
     * 刪除願望。只允許刪除草稿——已公開過的願望即使下架也保留，
     * 這樣認領紀錄與稽核軌跡才不會出現指向不存在資料的斷點。
     */
    @Transactional
    public void delete(UUID id) {
        Wish wish = findOwned(id);
        if (!wish.isDeletable()) {
            throw new BusinessRuleException("WISH_NOT_DELETABLE",
                    "只有草稿能刪除；已公開過的願望請改為下架");
        }
        wishes.delete(wish);
    }

    @Transactional(readOnly = true)
    public Page<Wish> listMine(WishStatus status, Pageable pageable) {
        UUID organizationId = currentUser.requireOrganizationId();
        return status == null
                ? wishes.findByOrganizationId(organizationId, pageable)
                : wishes.findByOrganizationIdAndStatus(organizationId, status, pageable);
    }

    // ---------------------------------------------------------------- 內部

    private Wish findById(UUID id) {
        return wishes.findWithOrganizationById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("願望", id));
    }

    /**
     * 取得願望，並確認它屬於目前的機構。
     *
     * <p>找不到與不屬於自己都回 404 而非 403：回 403 等於告訴對方「這個 id 存在」，
     * 讓人可以用列舉的方式探測其他機構有哪些願望。
     */
    private Wish findOwned(UUID id) {
        UUID organizationId = currentUser.requireOrganizationId();
        Wish wish = findById(id);
        if (!wish.getOrganization().getId().equals(organizationId)) {
            throw ResourceNotFoundException.of("願望", id);
        }
        return wish;
    }
}
