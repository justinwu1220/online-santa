package com.onlinesanta.leaderboard;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.closing.Campaign;
import com.onlinesanta.closing.CampaignRepository;
import com.onlinesanta.closing.CampaignService;
import com.onlinesanta.common.TaiwanYear;
import com.onlinesanta.leaderboard.dto.LeaderboardView;
import com.onlinesanta.leaderboard.dto.MyAchievementView;
import com.onlinesanta.leaderboard.dto.PodiumHistoryView;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.wish.WishRepository;
import com.onlinesanta.wish.WishStatus;

/**
 * 聖誕老人排行榜（SPEC 第 1 節）。
 *
 * <p>口徑：
 * <ul>
 *   <li><b>一份</b>＝一筆 {@code COMPLETED} 的認領（機構按下「確認收到並完成」），不看禮物數量；</li>
 *   <li><b>名次</b>算的是<b>當期</b>份數：認領的 {@code receivedOn} 落在
 *       {@code [本檔期勸募開始日, 下個檔期勸募開始日)}，與收據、結案文件同一套規則；
 *       同份數並列、下一位跳號；</li>
 *   <li><b>稱號</b>算的是<b>累計</b>份數（不分檔期）：解鎖之後永久持有，不會因為新的一屆重新開始
 *       （{@link Tier}）。</li>
 * </ul>
 *
 * <p>名字預設不公開：沒有自己開啟 {@code leaderboardOptIn}、或帳號被停權的人，
 * 榜上一律顯示「匿名的聖誕老人」，但份數與名次照算。
 *
 * <p>不做快取：這是兩句 group by，而且 SPEC 要求機構確認收到後「我的成就」要立刻反映。
 */
@Service
public class LeaderboardService {

    public static final String ANONYMOUS_NAME = "匿名的聖誕老人";

    /**
     * 目標份數算這個檔期的<strong>所有</strong>願望：可認領、已認領、已完成都算。
     * 草稿與審核中的不算（還沒上過願望牆），凍結過的照算（它確實上架過）。
     */
    private static final List<WishStatus> LISTED =
            List.of(WishStatus.AVAILABLE, WishStatus.CLAIMED, WishStatus.FULFILLED);

    /** 沒有下一個檔期時，區間一路開到底。 */
    private static final LocalDate FOREVER = LocalDate.of(9999, 12, 31);

    private final ClaimRepository claims;
    private final WishRepository wishes;
    private final CampaignService campaignService;
    private final CampaignRepository campaigns;
    private final CampaignPodiumRepository podiums;
    private final UserRepository users;

    public LeaderboardService(ClaimRepository claims, WishRepository wishes, CampaignService campaignService,
                              CampaignRepository campaigns, CampaignPodiumRepository podiums, UserRepository users) {
        this.claims = claims;
        this.wishes = wishes;
        this.campaignService = campaignService;
        this.campaigns = campaigns;
        this.podiums = podiums;
        this.users = users;
    }

    /**
     * 目前這一屆的排行榜。{@code limit} 只取前幾名（首頁入口只需要前三名，不必把整份名單搬過去）。
     *
     * <p>回應對所有人都一樣，CDN 才擋得住；結果另外在記憶體快取 {@value #CACHE_SECONDS} 秒。
     * 首頁每開一次就會打這支 API，這是整個功能唯一的高流量入口。名次晚幾分鐘更新無所謂，
     * 「我的成就」走 {@link #mine} 即時算。
     */
    @Transactional(readOnly = true)
    public LeaderboardView board(Integer limit) {
        Board board = cached();
        List<Standing> standings = board.standings();
        if (limit != null && limit > 0 && limit < standings.size()) {
            standings = standings.subList(0, limit);
        }
        List<LeaderboardView.Entry> entries = standings.stream()
                .map(s -> new LeaderboardView.Entry(s.rank(), s.publicName(), s.delivered(), s.tier(), !s.named()))
                .toList();
        return new LeaderboardView(board.campaignName(), board.goal(), board.delivered(),
                board.standings().size(), entries);
    }

    /**
     * 本人的當期成績與累計解鎖的稱號。
     *
     * <p>不吃快取：機構按下「確認收到並完成」之後要立刻看得到（SPEC 第 8 節）。
     * 這支只有登入者開排行榜頁時會打，量小。
     */
    @Transactional(readOnly = true)
    public MyAchievementView mine(UUID userId, boolean showName) {
        Map<UUID, Integer> lifetime = lifetimeCounts();
        int total = lifetime.getOrDefault(userId, 0);
        Tier tier = Tier.of(total);
        Tier next = Tier.next(total);
        int toNext = next == null ? 0 : next.min() - total;

        Campaign campaign = currentCampaign().orElse(null);
        Standing me = campaign == null ? null
                : standings(campaign, lifetime).stream()
                        .filter(s -> s.userId().equals(userId)).findFirst().orElse(null);

        if (me == null) {
            return new MyAchievementView(false, 0, null, total, tier, next, toNext, showName);
        }
        return new MyAchievementView(true, me.delivered(), me.rank(), total, tier, next, toNext, showName);
    }

    /**
     * 把這個檔期的前三名寫進榮譽榜（結案時呼叫）。
     *
     * <p>並列時同名次，所以可能存超過三筆（兩個第 1 名、一個第 3 名）。
     * 重跑只更新同一列；名次因為資料更正而掉出前三的，連同舊紀錄一起刪掉。
     *
     * @return 這一屆記下來的人數
     */
    @Transactional
    public int recordPodium(UUID campaignId) {
        Campaign campaign = campaignService.get(campaignId);
        List<Standing> top = standings(campaign, lifetimeCounts()).stream()
                .filter(s -> s.rank() <= 3)
                .toList();

        Map<UUID, CampaignPodium> existing = new HashMap<>();
        podiums.findByCampaignId(campaignId).forEach(row -> existing.put(row.getUserId(), row));

        List<CampaignPodium> keep = new ArrayList<>();
        for (Standing s : top) {
            CampaignPodium row = existing.remove(s.userId());
            if (row == null) {
                keep.add(new CampaignPodium(campaignId, s.userId(), s.rank(), s.delivered()));
            } else {
                row.update(s.rank(), s.delivered());
                keep.add(row);
            }
        }
        podiums.saveAll(keep);
        podiums.deleteAll(existing.values());   // 已經不在前三名的舊紀錄
        return keep.size();
    }

    /** 歷屆前三名，新的一屆在前。名字沿用目前的公開設定。 */
    @Transactional(readOnly = true)
    public PodiumHistoryView podiumHistory() {
        List<CampaignPodium> rows = podiums.findAll();
        if (rows.isEmpty()) {
            return new PodiumHistoryView(List.of());
        }
        Map<UUID, Campaign> campaignById = new HashMap<>();
        campaigns.findAllById(rows.stream().map(CampaignPodium::getCampaignId).distinct().toList())
                .forEach(c -> campaignById.put(c.getId(), c));
        Map<UUID, User> userById = new HashMap<>();
        users.findAllById(rows.stream().map(CampaignPodium::getUserId).distinct().toList())
                .forEach(u -> userById.put(u.getId(), u));

        Map<UUID, List<CampaignPodium>> byCampaign = new HashMap<>();
        rows.forEach(row -> byCampaign.computeIfAbsent(row.getCampaignId(), key -> new ArrayList<>()).add(row));

        List<PodiumHistoryView.CampaignPodiumView> views = byCampaign.entrySet().stream()
                .filter(entry -> campaignById.containsKey(entry.getKey()))
                .sorted(Comparator.comparing((Map.Entry<UUID, List<CampaignPodium>> entry) ->
                        campaignById.get(entry.getKey()).getCollectionStart()).reversed())
                .map(entry -> new PodiumHistoryView.CampaignPodiumView(
                        campaignById.get(entry.getKey()).getName(),
                        entry.getValue().stream()
                                .sorted(Comparator.comparingInt(CampaignPodium::getRank)
                                        .thenComparing(CampaignPodium::getDelivered, Comparator.reverseOrder()))
                                .map(row -> {
                                    User user = userById.get(row.getUserId());
                                    boolean named = user != null && user.isLeaderboardOptIn() && !user.isSuspended();
                                    return new PodiumHistoryView.CampaignPodiumView.Entry(row.getRank(),
                                            named ? user.getDisplayName() : ANONYMOUS_NAME,
                                            row.getDelivered(), !named);
                                })
                                .toList()))
                .toList();
        return new PodiumHistoryView(views);
    }

    // ------------------------------------------------------------ 快取

    /**
     * 算好的一份名單。放在記憶體裡就好——這個服務是 scale to zero 的 Cloud Run，
     * 多一個 Redis 之類的元件要付的錢與維護成本，遠高於它能省下的兩句 group by。
     * 執行個體被回收後下一個請求重算一次即可。
     */
    private record Board(String campaignName, int goal, int delivered, List<Standing> standings, Instant at) {
    }

    /** 名單快取多久。首頁入口的流量都吃這一份。 */
    static final int CACHE_SECONDS = 300;

    private volatile Board cache;

    private Board cached() {
        Board current = cache;
        Instant now = Instant.now();
        if (current != null && current.at().plusSeconds(CACHE_SECONDS).isAfter(now)) {
            return current;
        }
        Board fresh = compute(now);
        cache = fresh;
        return fresh;
    }

    /** 清掉名單快取（測試與資料大幅變動時用）。 */
    public void evict() {
        cache = null;
    }

    private Board compute(Instant now) {
        Campaign campaign = currentCampaign().orElse(null);
        if (campaign == null) {
            return new Board(null, 0, 0, List.of(), now);
        }
        List<Standing> standings = standings(campaign, lifetimeCounts());
        int delivered = standings.stream().mapToInt(Standing::delivered).sum();
        return new Board(campaign.getName(), goalOf(campaign), delivered, standings, now);
    }

    // ------------------------------------------------------------ 內部

    /** 今天所屬的檔期；一個檔期都還沒建立時是 empty。 */
    private Optional<Campaign> currentCampaign() {
        return campaignService.campaignForReceivedOn(LocalDate.now(TaiwanYear.ZONE));
    }

    /** 每位認領者的累計送達份數（稱號看這個，不分檔期）。 */
    private Map<UUID, Integer> lifetimeCounts() {
        Map<UUID, Integer> totals = new HashMap<>();
        for (Object[] row : claims.donorLifetimeDeliveredCounts()) {
            totals.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return totals;
    }

    /** 一位上榜者的當期成績。{@code named} 為 false 代表不公開名字。 */
    private record Standing(UUID userId, String publicName, int delivered, int rank, Tier tier, boolean named) {
    }

    private List<Standing> standings(Campaign campaign, Map<UUID, Integer> lifetime) {
        LocalDate from = campaign.getCollectionStart();
        LocalDate toExclusive = campaignService.nextCollectionStartAfter(from).orElse(FOREVER);

        // 完成了就算：凍結只擋新認領，停權也不回溯已經送到孩子手上的禮物
        List<Object[]> rows = claims.donorDeliveredCounts(from, toExclusive);
        List<Standing> standings = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            UUID userId = (UUID) row[0];
            String displayName = (String) row[1];
            boolean named = row[2] != null && row[3] == null;   // 自己開啟公開、且沒有被停權
            int delivered = ((Number) row[4]).intValue();
            standings.add(new Standing(userId, named ? displayName : ANONYMOUS_NAME, delivered, 0,
                    Tier.of(lifetime.getOrDefault(userId, delivered)), named));
        }
        standings.sort(Comparator.comparingInt(Standing::delivered).reversed());

        // 同份數並列同一名次，下一位跳號（兩個第 6 名，下一位是第 8 名）
        List<Standing> ranked = new ArrayList<>(standings.size());
        int rank = 0;
        int previous = -1;
        for (int i = 0; i < standings.size(); i++) {
            Standing s = standings.get(i);
            if (s.delivered() != previous) {
                rank = i + 1;
                previous = s.delivered();
            }
            ranked.add(new Standing(s.userId(), s.publicName(), s.delivered(), rank, s.tier(), s.named()));
        }
        return ranked;
    }

    /**
     * 目標份數＝這個檔期的願望總數（可認領＋已認領＋已完成）。
     *
     * <p>區間的結尾不是上架結束日，而是下一個檔期開始上架的那一天：上架期結束後才被管理員
     * 核准的願望，照樣會出現在願望牆上、被認領、被送達，算它是這個檔期的，目標才不會小於
     * 實際送達的份數。
     */
    private int goalOf(Campaign campaign) {
        Instant from = TaiwanYear.startOfDay(campaign.getWishListStart());
        Instant to = TaiwanYear.startOfDay(
                campaignService.nextWishListStartAfter(campaign.getWishListStart()).orElse(FOREVER));
        return (int) wishes.countListedBetween(LISTED, from, to);
    }
}
