package com.onlinesanta.admin;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.admin.dto.PlatformStatsView;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.claim.ClaimStatus;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.organization.OrganizationStatus;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.user.UserRole;
import com.onlinesanta.wish.WishRepository;
import com.onlinesanta.wish.WishStatus;

/**
 * 監控中心的全站統計。
 *
 * <p>全部走 {@code GROUP BY} 聚合查詢——四次查詢就把整站的狀態分佈算完，
 * 不論資料量多大成本都一樣。撈出全部再用 Java 數，在活動尖峰時會是災難。
 */
@Service
public class AdminStatsService {

    private final OrganizationRepository organizations;
    private final WishRepository wishes;
    private final ClaimRepository claims;
    private final UserRepository users;

    public AdminStatsService(OrganizationRepository organizations,
                             WishRepository wishes,
                             ClaimRepository claims,
                             UserRepository users) {
        this.organizations = organizations;
        this.wishes = wishes;
        this.claims = claims;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public PlatformStatsView collect() {
        Map<String, Long> organizationCounts =
                counts(organizations.countByStatus(), OrganizationStatus.values());
        Map<String, Long> wishCounts =
                counts(wishes.countByStatus(), WishStatus.values());

        return new PlatformStatsView(
                organizationCounts,
                wishCounts,
                counts(claims.countByStatus(), ClaimStatus.values()),
                counts(users.countByRole(), UserRole.values()),
                claims.countOverdue(Instant.now()),
                organizationCounts.getOrDefault(OrganizationStatus.PENDING.name(), 0L),
                wishCounts.getOrDefault(WishStatus.AVAILABLE.name(), 0L),
                Instant.now());
    }

    /**
     * 把 {@code [enum, count]} 的查詢結果轉成 map，並補齊所有可能的狀態。
     *
     * <p>沒有任何資料的狀態不會出現在查詢結果裡。補成 0 之後，前端就能穩定地畫出
     * 完整的分佈，不必處理「這個狀態這次不見了」。用 LinkedHashMap 保持 enum 的
     * 宣告順序，也就是流程的自然順序。
     */
    private Map<String, Long> counts(List<Object[]> rows, Enum<?>[] allStates) {
        Map<String, Long> found = new LinkedHashMap<>();
        for (Object[] row : rows) {
            found.put(((Enum<?>) row[0]).name(), (Long) row[1]);
        }

        Map<String, Long> complete = new LinkedHashMap<>();
        for (Enum<?> state : allStates) {
            complete.put(state.name(), found.getOrDefault(state.name(), 0L));
        }
        return complete;
    }
}
