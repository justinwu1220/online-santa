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
import com.onlinesanta.common.dto.DailyCount;
import com.onlinesanta.closing.OperatingPeriod;
import com.onlinesanta.closing.dto.OperatingPeriodView;
import com.onlinesanta.common.dto.PeriodMonthCount;
import com.onlinesanta.organization.dto.OrganizationPeriodStatsView;
import com.onlinesanta.organization.dto.OrganizationMonthlyStatsView;
import com.onlinesanta.wish.WishRepository;

/**
 * 機構後台的檔期回顧統計。
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
    public OrganizationPeriodStatsView period(OperatingPeriod period) {
        UUID organizationId = currentUser.requireOrganizationId();
        Instant from = period.from();
        Instant to = period.to();

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
        long completedAfterPeriod = claims.countCompletedAfterPeriod(organizationId, from, to);

        List<PeriodMonthCount> monthlyClaims = PeriodMonthCount.fill(period.months(),
                claims.monthlyClaimsForOrganization(organizationId, from, to));

        return new OrganizationPeriodStatsView(
                OperatingPeriodView.from(period), newWishes, claimed, completed, completionRate,
                released, cancelled, autoReleased, averageCompletionDays, completedAfterPeriod,
                monthlyClaims);
    }

    /** 「每月分布」長條圖點選某月後的下鑽：該月每日的認領分布。 */
    @Transactional(readOnly = true)
    public OrganizationMonthlyStatsView monthly(int year, int month) {
        UUID organizationId = currentUser.requireOrganizationId();
        Instant from = TaiwanYear.startOfMonth(year, month);
        Instant to = TaiwanYear.endOfMonth(year, month);

        List<DailyCount> dailyClaims = DailyCount.fill(year, month,
                claims.dailyClaimsForOrganization(organizationId, from, to));

        return new OrganizationMonthlyStatsView(year, month, dailyClaims);
    }
}
