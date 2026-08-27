package com.onlinesanta.job;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimEvent;
import com.onlinesanta.claim.ClaimEventRepository;
import com.onlinesanta.claim.ClaimEventType;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.job.dto.ReleaseSweepResult;
import com.onlinesanta.organization.ReleasePolicy;
import com.onlinesanta.wish.WishRepository;

/**
 * 逾期未寄送的認領處理。
 *
 * <p>去年的經驗是願望供不應求，認領後遲遲未寄送會讓孩子的願望一直卡著，其他想幫忙的
 * 人也領不到。因此提供逾期釋回；但機構對自家孩子的狀況最清楚，是否真的收回由機構
 * 自己決定：
 *
 * <ul>
 *   <li><b>AUTO</b>——逾期即自動釋回，願望重新上架</li>
 *   <li><b>MANUAL</b>——只在後台標記逾期，機構聯繫捐贈者後自行決定</li>
 * </ul>
 *
 * <p>判斷依據是認領當下的政策<em>快照</em>，不是機構現在的設定：已經在準備禮物的人
 * 不該因為機構臨時改了設定而被無預警收回。
 */
@Service
public class ClaimReleaseService {

    private static final Logger log = LoggerFactory.getLogger(ClaimReleaseService.class);
    private static final String AUTO_RELEASE_REASON = "逾期未上傳寄送證明，系統自動釋回";

    private final ClaimRepository claims;
    private final ClaimEventRepository events;
    private final WishRepository wishes;

    public ClaimReleaseService(ClaimRepository claims,
                               ClaimEventRepository events,
                               WishRepository wishes) {
        this.claims = claims;
        this.events = events;
        this.wishes = wishes;
    }

    /**
     * 掃出所有逾期的認領並依政策處理。
     *
     * <p>整批放在同一個交易裡：一天的量最多幾百筆，而「要嘛全部釋回、要嘛全部不動」
     * 比處理到一半失敗留下不一致的狀態好判斷。
     */
    @Transactional
    public ReleaseSweepResult sweep() {
        Instant now = Instant.now();
        List<Claim> overdue = claims.findOverdue(now);

        List<UUID> releasedWishIds = new ArrayList<>();
        int flaggedOnly = 0;

        for (Claim claim : overdue) {
            if (claim.getReleasePolicySnapshot() == ReleasePolicy.AUTO) {
                claim.release(AUTO_RELEASE_REASON);
                events.save(ClaimEvent.bySystem(
                        claim.getId(), ClaimEventType.RELEASED_AUTO, AUTO_RELEASE_REASON));
                releasedWishIds.add(claim.getWish().getId());
            } else {
                // 手動政策：只留在後台的逾期清單裡，等機構自己決定
                flaggedOnly++;
            }
        }

        // 所有 Claim 都改完之後才動願望：markAvailableAgainAll 會清空持久化脈絡，
        // 放在迴圈裡會讓還沒處理的 Claim 變成分離狀態而丟失變更
        int returnedToWall = releasedWishIds.isEmpty()
                ? 0
                : wishes.markAvailableAgainAll(releasedWishIds);

        ReleaseSweepResult result = new ReleaseSweepResult(
                now, overdue.size(), releasedWishIds.size(), returnedToWall, flaggedOnly);

        if (result.hasWork()) {
            log.info("逾期認領掃描：共 {} 筆逾期，自動釋回 {} 筆（願望回到牆上 {} 個），"
                            + "標記待機構處理 {} 筆",
                    result.overdueFound(), result.autoReleased(),
                    result.wishesReturnedToWall(), result.flaggedForOrganization());
        } else {
            log.debug("逾期認領掃描：沒有逾期的認領");
        }
        return result;
    }
}
