package com.onlinesanta.admin;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.admin.dto.OrganizationCompletionRankingView;
import com.onlinesanta.admin.dto.PlatformAnnualStatsView;
import com.onlinesanta.admin.dto.PlatformMonthlyStatsView;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.claim.ClaimStatus;
import com.onlinesanta.common.TaiwanYear;
import com.onlinesanta.common.dto.DailyCount;
import com.onlinesanta.common.dto.MonthlyCount;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.user.UserRole;
import com.onlinesanta.wish.WishRepository;

/**
 * 監控中心的年度營運統計。
 *
 * <p>與 {@link AdminStatsService} 分開：那裡是不分年度的全站現況分佈，這裡是
 * cohort 制的年度彙總，兩者的統計口徑不同（見 {@link com.onlinesanta.common.TaiwanYear}）。
 */
@Service
public class AdminAnnualStatsService {

    private static final int TOP_ORGANIZATIONS_LIMIT = 5;

    private final OrganizationRepository organizations;
    private final WishRepository wishes;
    private final ClaimRepository claims;
    private final UserRepository users;

    public AdminAnnualStatsService(OrganizationRepository organizations, WishRepository wishes,
                                   ClaimRepository claims, UserRepository users) {
        this.organizations = organizations;
        this.wishes = wishes;
        this.claims = claims;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public PlatformAnnualStatsView annual(Integer year) {
        int resolvedYear = year != null ? year : TaiwanYear.currentYear();
        Instant from = TaiwanYear.startOf(resolvedYear);
        Instant to = TaiwanYear.endOf(resolvedYear);

        long newDonors = users.countByRoleAndCreatedAtBetween(UserRole.DONOR, from, to);
        long newOrganizations = organizations.countByCreatedAtBetween(from, to);
        long activeDonors = claims.countDistinctActiveDonors(from, to);
        long publishedWishes = wishes.countByCreatedAtBetween(from, to);

        Object[] claimAggregate = claims.platformAnnualClaimAggregate(from, to).get(0);
        long claimed = (Long) claimAggregate[0];
        long completed = (Long) claimAggregate[1];
        double completionRate = claimed == 0 ? 0.0 : (double) completed / claimed;

        List<MonthlyCount> monthlyClaims = MonthlyCount.fill(claims.monthlyClaimsPlatformWide(from, to));
        Map<String, Long> claimOutcomes = claimOutcomes(claims.claimOutcomeCountsPlatformWide(from, to));

        List<OrganizationCompletionRankingView> topOrganizations = claims
                .topOrganizationsByCompletedClaims(from, to, PageRequest.of(0, TOP_ORGANIZATIONS_LIMIT))
                .stream()
                .map(row -> new OrganizationCompletionRankingView(
                        (UUID) row[0], (String) row[1], (Long) row[2]))
                .toList();

        List<Integer> availableYears = TaiwanYear.availableYearsSince(claims.earliestClaimedAtPlatformWide());

        return new PlatformAnnualStatsView(
                resolvedYear, newDonors, newOrganizations, activeDonors, publishedWishes,
                claimed, completed, completionRate, monthlyClaims, claimOutcomes,
                topOrganizations, availableYears);
    }

    /** 「每月趨勢」長條圖點選某月後的下鑽：該月每日的認領分布。 */
    @Transactional(readOnly = true)
    public PlatformMonthlyStatsView monthly(int year, int month) {
        Instant from = TaiwanYear.startOfMonth(year, month);
        Instant to = TaiwanYear.endOfMonth(year, month);

        List<DailyCount> dailyClaims = DailyCount.fill(year, month, claims.dailyClaimsPlatformWide(from, to));

        return new PlatformMonthlyStatsView(year, month, dailyClaims);
    }

    /**
     * 認領結果分布補零：三種終局狀態即使該年度沒有任何一筆，也要出現在回應裡，
     * 前端才能穩定畫出完整的圓餅圖（比照 {@code AdminStatsService.counts()} 的做法）。
     */
    private Map<String, Long> claimOutcomes(List<Object[]> rows) {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put(ClaimStatus.COMPLETED.name(), 0L);
        result.put(ClaimStatus.RELEASED.name(), 0L);
        result.put(ClaimStatus.CANCELLED.name(), 0L);
        for (Object[] row : rows) {
            result.put(((Enum<?>) row[0]).name(), (Long) row[1]);
        }
        return result;
    }
}
