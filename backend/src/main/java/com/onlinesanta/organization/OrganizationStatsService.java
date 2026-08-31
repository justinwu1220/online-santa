package com.onlinesanta.organization;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.claim.ClaimEventRepository;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.common.TaiwanYear;
import com.onlinesanta.common.dto.MonthlyCount;
import com.onlinesanta.organization.dto.OrganizationAnnualStatsView;
import com.onlinesanta.wish.WishRepository;

/**
 * 機構後台的年度回顧統計。
 *
 * <p><strong>安全注意：</strong>{@code /api/organizations/me/**} 沒有路徑層級的角色限制，
 * 全靠這裡呼叫 {@link CurrentUserService#requireOrganizationId()} 擋人——任何登入者都打得到
 * 這個端點，必須由這一行確保只有機構成員、且只看得到自己機構的數字。
 */
@Service
public class OrganizationStatsService {

    private final ClaimRepository claims;
    private final ClaimEventRepository claimEvents;
    private final WishRepository wishes;
    private final CurrentUserService currentUser;

    public OrganizationStatsService(ClaimRepository claims, ClaimEventRepository claimEvents,
                                    WishRepository wishes, CurrentUserService currentUser) {
        this.claims = claims;
        this.claimEvents = claimEvents;
        this.wishes = wishes;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public OrganizationAnnualStatsView annual(Integer year) {
        UUID organizationId = currentUser.requireOrganizationId();
        int resolvedYear = year != null ? year : TaiwanYear.currentYear();
        Instant from = TaiwanYear.startOf(resolvedYear);
        Instant to = TaiwanYear.endOf(resolvedYear);

        long newWishes = wishes.countByOrganizationAndCreatedAtBetween(organizationId, from, to);

        Object[] statusAggregate =
                claims.organizationAnnualStatusAggregate(organizationId, from, to).get(0);
        long claimed = (Long) statusAggregate[0];
        long completed = (Long) statusAggregate[1];
        long released = (Long) statusAggregate[2];
        long cancelled = (Long) statusAggregate[3];
        double completionRate = claimed == 0 ? 0.0 : (double) completed / claimed;

        long autoReleased = claimEvents.countAutoReleasedForOrganization(organizationId, from, to);
        Double averageCompletionDays = claims.averageCompletionDaysForOrganization(organizationId, from, to);
        long crossYearCompletions = claims.countCrossYearCompletions(organizationId, from, to);

        List<MonthlyCount> monthlyClaims = MonthlyCount.fill(
                claims.monthlyClaimsForOrganization(organizationId, from, to));

        List<Integer> availableYears = TaiwanYear.availableYearsSince(
                claims.earliestClaimedAtByOrganization(organizationId));

        return new OrganizationAnnualStatsView(
                resolvedYear, newWishes, claimed, completed, completionRate,
                released, cancelled, autoReleased, averageCompletionDays, crossYearCompletions,
                monthlyClaims, availableYears);
    }
}
