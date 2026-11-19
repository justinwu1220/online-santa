package com.onlinesanta.closing;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.closing.dto.OperatingPeriodView;
import com.onlinesanta.closing.dto.PeriodListView;
import com.onlinesanta.common.TaiwanYear;
import com.onlinesanta.common.exception.ResourceNotFoundException;
import com.onlinesanta.wish.WishRepository;

/**
 * 把活動檔期換算成營運統計的期間（見 {@link OperatingPeriod}）。
 *
 * <p>檔期的勸募期間不可重疊（{@link CampaignService}），所以依勸募開始日排序後，每個檔期的範圍是
 * {@code [本檔期勸募開始日, 下個檔期勸募開始日)}，所有日期剛好各屬於一個期間（與收據歸屬同一個定義）；
 * 第一個檔期往前涵蓋更早的資料，最後一個檔期往後到今天。
 */
@Service
public class OperatingPeriodService {

    private final CampaignRepository campaigns;
    private final ClaimRepository claims;
    private final WishRepository wishes;

    public OperatingPeriodService(CampaignRepository campaigns, ClaimRepository claims, WishRepository wishes) {
        this.campaigns = campaigns;
        this.claims = claims;
        this.wishes = wishes;
    }

    /** 所有期間，由新到舊；最後一個檔期結束後仍有時間，就多一個「尚未建立檔期」。 */
    @Transactional(readOnly = true)
    public List<OperatingPeriod> all() {
        return all(LocalDate.now(TaiwanYear.ZONE));
    }

    List<OperatingPeriod> all(LocalDate today) {
        List<Campaign> ordered = campaigns.findAll().stream()
                .sorted(Comparator.comparing(Campaign::getCollectionStart).thenComparing(Campaign::getCollectionEnd))
                .toList();

        if (ordered.isEmpty()) {
            return List.of(OperatingPeriod.of(OperatingPeriod.UNASSIGNED, null, "尚未建立檔期",
                    firstStart(today), today, true));
        }
        List<OperatingPeriod> periods = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            Campaign campaign = ordered.get(i);
            boolean first = i == 0;
            boolean last = i == ordered.size() - 1;
            LocalDate start = first ? firstStart(campaign.getCollectionStart()) : campaign.getCollectionStart();
            // 範圍是 [本檔期勸募開始日, 下個檔期勸募開始日)；最後一個檔期往後一路到今天（或它的勸募結束日）
            LocalDate end = last
                    ? (today.isAfter(campaign.getCollectionEnd()) ? today : campaign.getCollectionEnd())
                    : ordered.get(i + 1).getCollectionStart().minusDays(1);
            periods.add(OperatingPeriod.of(campaign.getId().toString(), campaign.getId(), campaign.getName(),
                    start, end, first));
        }
        return periods.reversed();
    }

    @Transactional(readOnly = true)
    public PeriodListView list() {
        LocalDate today = LocalDate.now(TaiwanYear.ZONE);
        List<OperatingPeriod> periods = all(today);
        return new PeriodListView(defaultOf(periods, today).key(),
                periods.stream().map(OperatingPeriodView::from).toList());
    }

    /** 依 key 找期間；key 空白時給預設選項。找不到回 404。 */
    @Transactional(readOnly = true)
    public OperatingPeriod resolve(String key) {
        LocalDate today = LocalDate.now(TaiwanYear.ZONE);
        List<OperatingPeriod> periods = all(today);
        if (key == null || key.isBlank()) {
            return defaultOf(periods, today);
        }
        return periods.stream().filter(p -> p.key().equals(key)).findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of("統計期間", parse(key)));
    }

    /** 涵蓋今天的檔期 → 已開始的最新檔期 → 最新的檔期 → 尚未建立檔期（沒有任何檔期時）。 */
    private static OperatingPeriod defaultOf(List<OperatingPeriod> periods, LocalDate today) {
        List<OperatingPeriod> byCampaign = periods.stream().filter(p -> !p.unassigned()).toList();
        return byCampaign.stream().filter(p -> p.contains(today)).findFirst()
                .or(() -> byCampaign.stream().filter(p -> !p.start().isAfter(today)).findFirst())
                .or(() -> byCampaign.stream().findFirst())
                .orElse(periods.get(0));
    }

    /** 第一個期間的顯示起點：最早的資料日（認領或願望），沒有就用給定的日期；不晚於 {@code fallback}。 */
    private LocalDate firstStart(LocalDate fallback) {
        Optional<Instant> earliest = java.util.stream.Stream.of(
                        Optional.ofNullable(claims.earliestClaimedAtPlatformWide()),
                        Optional.ofNullable(wishes.earliestCreatedAt()))
                .flatMap(Optional::stream)
                .min(Comparator.naturalOrder());
        return earliest.map(i -> LocalDate.ofInstant(i, TaiwanYear.ZONE))
                .filter(d -> d.isBefore(fallback))
                .orElse(fallback);
    }

    private static UUID parse(String key) {
        try {
            return UUID.fromString(key);
        } catch (IllegalArgumentException e) {
            return new UUID(0, 0);
        }
    }
}
