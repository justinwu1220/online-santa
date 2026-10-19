package com.onlinesanta.wish;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.attachment.AttachmentService;
import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.common.TaiwanYear;
import com.onlinesanta.common.exception.BusinessRuleException;
import com.onlinesanta.common.exception.ResourceNotFoundException;
import com.onlinesanta.message.SystemMessageService;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationService;
import com.onlinesanta.wish.dto.WishFilterOptions;
import com.onlinesanta.wish.dto.WishRequest;

@Service
public class WishService {

    /**
     * 願望牆「全部」狀態的預設可見範圍：可認領、已認領、已完成。
     *
     * <p>草稿與審核中一律不開放——跟 {@link Wish#isPubliclyVisible()} 的精神一致。
     */
    private static final List<WishStatus> DEFAULT_WALL_STATUSES =
            List.of(WishStatus.AVAILABLE, WishStatus.CLAIMED, WishStatus.FULFILLED);

    private final WishRepository wishes;
    private final OrganizationService organizations;
    private final CurrentUserService currentUser;
    private final AttachmentService attachments;
    private final WishEventService history;
    private final SystemMessageService systemMessages;

    public WishService(WishRepository wishes,
                       OrganizationService organizations,
                       CurrentUserService currentUser,
                       AttachmentService attachments,
                       WishEventService history,
                       SystemMessageService systemMessages) {
        this.wishes = wishes;
        this.organizations = organizations;
        this.currentUser = currentUser;
        this.attachments = attachments;
        this.history = history;
        this.systemMessages = systemMessages;
    }

    // ---------------------------------------------------------------- 公開瀏覽

    /**
     * 願望牆：預設顯示可認領／已認領／已完成（不帶 {@code status} 篩選時），草稿與
     * 下架一律不會出現。帶 {@code status} 時只篩單一狀態。其餘條件皆為選填。
     */
    @Transactional(readOnly = true)
    public Page<Wish> browse(WishCategory category, AgeRange ageRange, UUID organizationId,
                             WishWallStatus status, Pageable pageable) {
        List<WishStatus> statuses = status == null
                ? DEFAULT_WALL_STATUSES
                : List.of(status.toWishStatus());
        return wishes.search(statuses, organizationId, category, ageRange, pageable);
    }

    /** 願望牆篩選選項：分類/年齡/價格為固定 enum，機構名稱來自已核准機構清單。 */
    @Transactional(readOnly = true)
    public WishFilterOptions filterOptions() {
        return WishFilterOptions.build(organizations.listApprovedForWishFilter());
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

    /**
     * 提交審核：草稿 → 審核中。機構尚未通過審核時只能存草稿；提交時需要已上傳願望信件照片
     * （設計稿：存成草稿只需要標題，提交審核才要求全部必填）。
     * 曾經上架過的願望（修改後重新送審）記為「重新送審」，管理員重審時看得到。
     */
    @Transactional
    public Wish submit(UUID id) {
        Wish wish = findOwned(id);
        if (attachments.wishLetters(id).isEmpty()) {
            throw new BusinessRuleException("WISH_LETTER_REQUIRED",
                    "請先上傳孩子的手寫願望信件照片，再提交審核");
        }
        boolean resubmission = wish.isTakenDown();
        wish.submitForReview();
        history.record(wish, resubmission ? WishEventType.RESUBMITTED : WishEventType.SUBMITTED,
                currentUser.require().userId(), null, true);
        return wish;
    }

    /** 撤回審核：審核中 → 草稿。 */
    @Transactional
    public Wish withdraw(UUID id) {
        Wish wish = findOwned(id);
        wish.withdraw();
        history.record(wish, WishEventType.WITHDRAWN, currentUser.require().userId(), null, false);
        return wish;
    }

    /**
     * 修改已上架的願望：先原子地從願望牆撤下（AVAILABLE → DRAFT，凍結中的一併解除），
     * 之後依一般草稿的流程修改、提交審核，核准才重新上架。
     *
     * <p>撤下是一句條件式 UPDATE，與認領搶同一列的鎖：認領先到，這裡就回 409，
     * 不會出現「審核中被認領」或「已被認領卻被改掉內容」。
     */
    @Transactional
    public Wish beginEdit(UUID id) {
        Wish before = findOwned(id);
        if (!before.getOrganization().canDraftWishes()) {
            throw new BusinessRuleException("ORGANIZATION_SUSPENDED",
                    "機構已停權，無法修改願望，請與平台管理員聯繫");
        }
        boolean wasFrozen = before.isFrozen();
        if (wishes.markTakenDownForEdit(id) == 0) {
            Wish now = findById(id);
            throw switch (now.getStatus()) {
                case CLAIMED, FULFILLED -> new BusinessRuleException("WISH_ALREADY_CLAIMED",
                        "這個願望已經被認領，內容已鎖定。若認領者還沒寄出，可以先收回認領再修改");
                default -> new BusinessRuleException("WISH_NOT_EDITABLE",
                        "只有上架中的願望需要先下架再修改，目前狀態為 " + now.getStatus());
            };
        }
        // 條件式 UPDATE 清空了持久化脈絡，重新載入
        Wish wish = findById(id);
        UUID actor = currentUser.require().userId();
        history.record(wish, WishEventType.TAKEN_DOWN_FOR_EDIT, actor, null, true);
        if (wasFrozen) {
            history.record(wish, WishEventType.FREEZE_LIFTED_BY_RESUBMIT, actor, null, false);
            systemMessages.freezeLiftedByEdit(id);
        }
        return wish;
    }

    /** 下架修改後，內容完全沒變就直接恢復上架（不必重審）；有任何修改一律要送審。 */
    @Transactional
    public Wish restore(UUID id) {
        Wish wish = findOwned(id);
        if (!history.canRestore(wish)) {
            throw new BusinessRuleException("WISH_NOT_RESTORABLE",
                    "內容已經修改過（或不是為修改而下架），請提交審核，通過後才會重新上架");
        }
        wish.restore();
        history.record(wish, WishEventType.RESTORED, currentUser.require().userId(), null, false);
        return wish;
    }

    /**
     * 刪除願望。只允許刪除從沒公開過的草稿——已公開過的願望即使下架、被退回也保留，
     * 這樣認領紀錄、稽核軌跡與重審時要看的歷程才不會出現指向不存在資料的斷點。
     */
    @Transactional
    public void delete(UUID id) {
        Wish wish = findOwned(id);
        if (!wish.isDeletable()) {
            throw new BusinessRuleException("WISH_NOT_DELETABLE",
                    "只有從沒公開過的草稿能刪除；曾經上架過的願望請保留（不需要時不用處理）");
        }
        wishes.delete(wish);
    }

    /**
     * @param year 選填的年度篩選（台北日曆年，以 {@code createdAt} 歸年）。null 代表不篩選，
     *             可與 status 任意組合
     */
    @Transactional(readOnly = true)
    public Page<Wish> listMine(WishStatus status, Integer year, Pageable pageable) {
        UUID organizationId = currentUser.requireOrganizationId();
        if (year == null) {
            return status == null
                    ? wishes.findByOrganizationId(organizationId, pageable)
                    : wishes.findByOrganizationIdAndStatus(organizationId, status, pageable);
        }

        Instant from = TaiwanYear.startOf(year);
        Instant to = TaiwanYear.endOf(year);
        return status == null
                ? wishes.findByOrganizationIdAndCreatedAtRange(organizationId, from, to, pageable)
                : wishes.findByOrganizationIdAndStatusAndCreatedAtRange(
                        organizationId, status, from, to, pageable);
    }

    /**
     * 機構「願望管理」頁年度篩選下拉的可選年份，以 createdAt 歸年。
     *
     * <p>機構後台沒有像監控中心 {@code /api/admin/stats} 那樣每頁都會打的統計端點可以
     * 搭便車（OrgLayout 打的是逾期計數，語意上跟這裡的「願望有哪些年份」無關），
     * 因此另開一支輕量端點，寫法比照既有的 {@code GET /api/wishes/options}
     * （選項類資料，非分頁清單，直接查即可）。
     */
    @Transactional(readOnly = true)
    public List<Integer> availableYears() {
        UUID organizationId = currentUser.requireOrganizationId();
        return TaiwanYear.availableYearsSince(wishes.earliestCreatedAtByOrganization(organizationId));
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
