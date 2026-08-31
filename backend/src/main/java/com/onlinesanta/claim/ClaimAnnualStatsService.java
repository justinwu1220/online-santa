package com.onlinesanta.claim;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.claim.dto.DonorAnnualSummaryView;
import com.onlinesanta.common.TaiwanYear;

/**
 * 捐贈者「我的認領」的年度小結。
 *
 * <p>與 {@link ClaimService} 分開：那裡是認領流程的寫入邏輯，這裡是純讀取的年度彙總，
 * 與監控中心的 {@code AdminStatsService}、機構的 {@code OrganizationStatsService} 對稱。
 */
@Service
public class ClaimAnnualStatsService {

    private final ClaimRepository claims;
    private final CurrentUserService currentUser;

    public ClaimAnnualStatsService(ClaimRepository claims, CurrentUserService currentUser) {
        this.claims = claims;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public DonorAnnualSummaryView annualSummary(Integer year) {
        UUID donorId = currentUser.require().userId();
        int resolvedYear = year != null ? year : TaiwanYear.currentYear();
        Instant from = TaiwanYear.startOf(resolvedYear);
        Instant to = TaiwanYear.endOf(resolvedYear);

        Object[] aggregate = claims.donorAnnualAggregate(donorId, from, to).get(0);
        List<Integer> availableYears =
                TaiwanYear.availableYearsSince(claims.earliestClaimedAtByDonor(donorId));

        return new DonorAnnualSummaryView(
                resolvedYear,
                (Long) aggregate[0],
                (Long) aggregate[1],
                (Long) aggregate[2],
                (Long) aggregate[3],
                availableYears);
    }
}
