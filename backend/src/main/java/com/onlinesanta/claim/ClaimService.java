package com.onlinesanta.claim;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.auth.AppPrincipal;
import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.claim.dto.ClaimRequest;
import com.onlinesanta.claim.dto.ReleaseRequest;
import com.onlinesanta.claim.dto.ShipRequest;
import com.onlinesanta.common.TaiwanYear;
import com.onlinesanta.common.exception.BusinessRuleException;
import com.onlinesanta.common.exception.ResourceNotFoundException;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.ReleasePolicy;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishRepository;

/**
 * 認領流程的全部業務邏輯。
 *
 * <p>最關鍵的是 {@link #claim}：上架首日會有大量民眾同時搶同一個願望，必須保證
 * 一個願望只會被一個人領到。作法見該方法的說明。
 */
@Service
public class ClaimService {

    private final ClaimRepository claims;
    private final ClaimEventRepository events;
    private final WishRepository wishes;
    private final UserRepository users;
    private final CurrentUserService currentUser;
    private final ClaimProperties properties;

    public ClaimService(ClaimRepository claims,
                        ClaimEventRepository events,
                        WishRepository wishes,
                        UserRepository users,
                        CurrentUserService currentUser,
                        ClaimProperties properties) {
        this.claims = claims;
        this.events = events;
        this.wishes = wishes;
        this.users = users;
        this.currentUser = currentUser;
        this.properties = properties;
    }

    // ================================================================ 認領

    /**
     * 認領一個願望。
     *
     * <p><strong>防超賣的兩道防線：</strong>
     *
     * <ol>
     *   <li><b>應用層</b>——{@link WishRepository#markClaimed} 是一句條件式 UPDATE
     *       （{@code WHERE status = 'AVAILABLE'}）。在 READ COMMITTED 下它會取得該列的
     *       row lock，後到的交易等前一筆 commit 後重讀，看到狀態已變而回傳 0 列。
     *       這一句本身就足以防止重複認領，且不需要先 SELECT 再 UPDATE 的兩次往返。
     *   <li><b>資料庫層</b>——{@code uq_active_claim_per_wish} 部分唯一索引保證同一願望
     *       不可能同時存在兩筆有效認領。即使日後有人寫了繞過本服務的程式碼，資料仍不會壞。
     * </ol>
     *
     * <p>順序上先檢查名額上限、再做 UPDATE：讓成本低的檢查先失敗，避免無謂地鎖住熱門列。
     */
    @Transactional
    public Claim claim(UUID wishId, ClaimRequest request) {
        // 機構要靠這個信箱聯繫捐贈者，寄送出問題時也只有這條路——必須是真的
        AppPrincipal principal = currentUser.requireVerifiedEmail();

        // 認領前先把需要的資料讀出來——UPDATE 之後持久化脈絡會被清空
        Wish wish = wishes.findWithOrganizationById(wishId)
                .orElseThrow(() -> ResourceNotFoundException.of("願望", wishId));
        Organization organization = wish.getOrganization();

        requireWithinDonorQuota(principal.userId());

        if (wishes.markClaimed(wishId) == 0) {
            // 走到這裡代表在我們讀取與更新之間，願望被別人領走或被機構下架了
            throw new BusinessRuleException("WISH_ALREADY_CLAIMED",
                    "手慢了一步，這個願望剛剛被其他人領走了");
        }

        ReleasePolicy policy = organization.getReleasePolicy();
        Instant deadline = calculateShipDeadline(organization);

        // markClaimed 的 clearAutomatically 清空了持久化脈絡，先前讀到的 wish 已成為
        // 分離狀態。這裡重新載入完整的實體（而非用 getReferenceById 取代理物件）：
        // open-in-view 是關閉的，交易結束後代理物件就無法初始化，controller 組 DTO 時
        // 會拋 LazyInitializationException。只有搶贏的那一次會付出這兩次查詢。
        Wish claimedWish = wishes.findWithOrganizationById(wishId)
                .orElseThrow(() -> ResourceNotFoundException.of("願望", wishId));
        User donor = users.findById(principal.userId())
                .orElseThrow(() -> ResourceNotFoundException.of("使用者", principal.userId()));

        Claim claim = claims.save(Claim.open(
                claimedWish, donor, policy, deadline, request.donorMessage()));

        record(claim, ClaimEventType.CLAIMED, principal.userId(), null);
        return claim;
    }

    /**
     * 名額上限只是公平性措施，不是資料完整性的保證。
     *
     * <p>同一人在極短時間內併發送出多筆認領時，這個檢查可能都放行而略微超出上限；
     * 真正必須嚴守的「一個願望只有一人領到」由條件式 UPDATE 與唯一索引保證。
     * 為了這種邊緣情況去鎖住使用者列，代價遠大於效益。
     */
    private void requireWithinDonorQuota(UUID donorId) {
        long active = claims.countByDonorIdAndStatusIn(donorId, ClaimStatus.occupyingStates());
        if (active >= properties.maxActivePerDonor()) {
            throw new BusinessRuleException("CLAIM_QUOTA_EXCEEDED",
                    "你目前有 %d 筆進行中的認領，已達上限 %d 筆。完成寄送後即可再認領。"
                            .formatted(active, properties.maxActivePerDonor()));
        }
    }

    /**
     * 兩種政策都會算出期限：AUTO 據此自動釋回，MANUAL 僅作為機構後台的逾期提示。
     * 天數在認領當下就固定下來，機構事後調整設定不影響既有認領。
     */
    private Instant calculateShipDeadline(Organization organization) {
        int days = organization.getReleasePolicy() == ReleasePolicy.AUTO
                && organization.getReleaseAfterDays() != null
                ? organization.getReleaseAfterDays()
                : properties.defaultReleaseAfterDays();
        return Instant.now().plus(days, ChronoUnit.DAYS);
    }

    // ================================================================ 捐贈者的操作

    @Transactional
    public Claim ship(UUID claimId, ShipRequest request) {
        AppPrincipal principal = currentUser.require();
        Claim claim = findOwnedByDonor(claimId, principal.userId());

        claim.markShipped(request.carrier(), request.trackingNumber());
        record(claim, ClaimEventType.SHIPPED, principal.userId(),
                "%s %s".formatted(request.carrier(), request.trackingNumber()));
        return claim;
    }

    /** 捐贈者主動取消，願望立刻重新上架讓其他人有機會。 */
    @Transactional
    public Claim cancel(UUID claimId, ReleaseRequest request) {
        AppPrincipal principal = currentUser.require();
        Claim claim = findOwnedByDonor(claimId, principal.userId());

        claim.cancel(request.reason());
        returnWishToWall(claim);
        record(claim, ClaimEventType.CANCELLED, principal.userId(), request.reason());
        return claim;
    }

    /**
     * @param year 選填的年度篩選（台北日曆年）；null 代表全部年度
     */
    @Transactional(readOnly = true)
    public Page<Claim> listMine(Integer year, Pageable pageable) {
        UUID donorId = currentUser.require().userId();
        if (year == null) {
            return claims.findByDonorIdOrderByClaimedAtDesc(donorId, pageable);
        }
        return claims.findByDonorIdAndClaimedAtBetween(
                donorId, TaiwanYear.startOf(year), TaiwanYear.endOf(year), pageable);
    }

    // ================================================================ 機構的操作

    @Transactional
    public Claim confirmReceived(UUID claimId) {
        AppPrincipal principal = currentUser.require();
        Claim claim = findOwnedByOrganization(claimId, currentUser.requireOrganizationId());

        claim.markReceived();
        record(claim, ClaimEventType.RECEIVED, principal.userId(), null);
        return claim;
    }

    @Transactional
    public Claim complete(UUID claimId) {
        AppPrincipal principal = currentUser.require();
        Claim claim = findOwnedByOrganization(claimId, currentUser.requireOrganizationId());

        claim.markCompleted();
        if (wishes.markFulfilled(claim.getWish().getId()) == 0) {
            throw new BusinessRuleException("WISH_STATE_MISMATCH",
                    "願望的狀態與認領不一致，請重新整理後再試");
        }
        record(claim, ClaimEventType.COMPLETED, principal.userId(), null);
        return claim;
    }

    /** 機構收回認領（逾期未寄、聯繫不上等），願望重新上架。 */
    @Transactional
    public Claim release(UUID claimId, ReleaseRequest request) {
        AppPrincipal principal = currentUser.require();
        Claim claim = findOwnedByOrganization(claimId, currentUser.requireOrganizationId());

        claim.release(request.reason());
        returnWishToWall(claim);
        record(claim, ClaimEventType.RELEASED_MANUAL, principal.userId(), request.reason());
        return claim;
    }

    /**
     * 本機構逾期未寄送的認領。
     *
     * <p>手動釋回政策的機構靠這份清單自行決定要不要收回；自動政策的機構理論上
     * 排程已經處理掉，這裡會是空的（除非排程還沒跑）。
     */
    @Transactional(readOnly = true)
    public Page<Claim> listOverdueForMyOrganization(Pageable pageable) {
        return claims.findOverdueByOrganizationId(
                currentUser.requireOrganizationId(), Instant.now(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Claim> listForMyOrganization(ClaimStatus status, Pageable pageable) {
        UUID organizationId = currentUser.requireOrganizationId();
        return status == null
                ? claims.findByOrganizationId(organizationId, pageable)
                : claims.findByOrganizationIdAndStatus(organizationId, status, pageable);
    }

    // ================================================================ 共用

    @Transactional(readOnly = true)
    public Claim getVisibleById(UUID claimId) {
        AppPrincipal principal = currentUser.require();
        Claim claim = findById(claimId);

        boolean isDonor = claim.isOwnedBy(principal.userId());
        boolean isOwningOrganization = principal.organizationId() != null
                && claim.getWish().getOrganization().getId().equals(principal.organizationId());

        if (!isDonor && !isOwningOrganization) {
            throw ResourceNotFoundException.of("認領", claimId);
        }
        return claim;
    }

    @Transactional(readOnly = true)
    public List<ClaimEvent> timelineOf(UUID claimId) {
        getVisibleById(claimId);  // 順便做權限檢查
        return events.findByClaimIdOrderByCreatedAtAsc(claimId);
    }

    /** 釋回或取消後把願望放回願望牆。 */
    private void returnWishToWall(Claim claim) {
        if (wishes.markAvailableAgain(claim.getWish().getId()) == 0) {
            throw new BusinessRuleException("WISH_STATE_MISMATCH",
                    "願望的狀態與認領不一致，請重新整理後再試");
        }
    }

    private void record(Claim claim, ClaimEventType type, UUID actorId, String note) {
        events.save(ClaimEvent.by(claim.getId(), type, actorId, note));
    }

    private Claim findById(UUID claimId) {
        return claims.findWithDetailsById(claimId)
                .orElseThrow(() -> ResourceNotFoundException.of("認領", claimId));
    }

    /**
     * 取得認領並確認操作者身分。找不到與無權限都回 404——回 403 等於承認該 id 存在，
     * 可被用來探測他人的認領紀錄。
     */
    private Claim findOwnedByDonor(UUID claimId, UUID donorId) {
        Claim claim = findById(claimId);
        if (!claim.isOwnedBy(donorId)) {
            throw ResourceNotFoundException.of("認領", claimId);
        }
        return claim;
    }

    private Claim findOwnedByOrganization(UUID claimId, UUID organizationId) {
        Claim claim = findById(claimId);
        if (!claim.getWish().getOrganization().getId().equals(organizationId)) {
            throw ResourceNotFoundException.of("認領", claimId);
        }
        return claim;
    }
}
