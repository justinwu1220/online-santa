package com.onlinesanta.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.onlinesanta.attachment.AttachmentPurpose;
import com.onlinesanta.closing.Campaign;
import com.onlinesanta.closing.CampaignService;
import com.onlinesanta.closing.ClosingExportService;
import com.onlinesanta.closing.dto.CampaignRequest;
import com.onlinesanta.common.TaiwanYear;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.organization.ReleasePolicy;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.ShipRequests;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishCategory;
import com.onlinesanta.wish.WishRepository;

@DisplayName("排行榜：份數與並列名次、預設匿名、目標份數、我的成就與歷屆前三名")
class LeaderboardApiIT extends ApiIntegrationTest {

    private static final String ORG_USER = "rank-org@example.org";
    private static final String TOP = "rank-top@example.com";
    private static final String SECOND = "rank-second@example.com";
    private static final String THIRD = "rank-third@example.com";

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    UserRepository users;

    @Autowired
    WishRepository wishes;

    @Autowired
    LeaderboardService leaderboard;

    @Autowired
    ClosingExportService exports;

    @Autowired
    CampaignService campaigns;

    private Campaign campaign;
    private Organization organization;

    @BeforeEach
    void setUp() {
        campaign = givenOpenWishListing();
        organization = Organization.register("排行之家", "王承辦", "contact@example.org", null, null, null);
        organization.approve(null, "測試資料");
        organizations.save(organization);
        User member = User.newDonor(ORG_USER, ORG_USER);
        member.joinOrganization(organization.getId());
        users.save(member);

        users.save(User.newDonor(TOP, "送最多的人"));
        users.save(User.newDonor(SECOND, "並列的甲"));
        users.save(User.newDonor(THIRD, "並列的乙"));
        leaderboard.evict();
    }

    /** 建一個已上架（可認領）的願望，回傳願望 id。 */
    private UUID listed(String title) {
        Wish wish = Wish.draft(organization, "小星", AgeRange.AGE_7_9, "畫畫", title, "描述",
                WishCategory.ART, PriceRange.UNDER_500);
        wish.publish();
        return wishes.save(wish).getId();
    }

    /** donor 認領一個願望（只認領，不寄出），回傳認領 id。 */
    private UUID claimOnly(String donor, UUID wishId) throws Exception {
        String body = mvc.perform(as(post("/api/wishes/{id}/claim", wishId), donor))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    /** 建一個已上架的願望，讓 donor 認領、寄出，機構確認收到並完成。 */
    private void deliver(String donor, String title) throws Exception {
        UUID claimId = claimOnly(donor, listed(title));

        uploadConfirmedAttachment(AttachmentPurpose.SHIPPING_PROOF, claimId, donor);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId), ShipRequests.of("郵局", "R" + title)), donor))
                .andExpect(status().isOk());
        completeAsOrganization(claimId, ORG_USER).andExpect(status().isOk());
        leaderboard.evict();
    }

    @Test
    @DisplayName("名單公開可讀：一個願望算一份、同份數並列、預設匿名，且不含任何識別資料")
    void boardIsPublicAnonymousAndTiesShareRank() throws Exception {
        deliver(TOP, "一號");
        deliver(TOP, "二號");
        deliver(SECOND, "三號");
        deliver(THIRD, "四號");

        String body = mvc.perform(get("/api/leaderboard"))      // 不帶 token
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivered").value(4))
                .andExpect(jsonPath("$.donorCount").value(3))
                .andExpect(jsonPath("$.entries.length()").value(3))
                .andExpect(jsonPath("$.entries[0].rank").value(1))
                .andExpect(jsonPath("$.entries[0].delivered").value(2))
                .andExpect(jsonPath("$.entries[0].tier").value("ELF"))
                .andExpect(jsonPath("$.entries[0].anonymous").value(true))
                .andExpect(jsonPath("$.entries[0].name").value("匿名的聖誕老人"))
                // 並列：兩個 1 份的都是第 2 名
                .andExpect(jsonPath("$.entries[1].rank").value(2))
                .andExpect(jsonPath("$.entries[2].rank").value(2))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("送最多的人").doesNotContain(TOP).doesNotContain("一號");
    }

    @Test
    @DisplayName("自己開啟「在排行榜顯示我的名字」之後，名字才會出現在榜上")
    void nameAppearsOnlyAfterOptIn() throws Exception {
        deliver(TOP, "一號");

        mvc.perform(as(withBody(put("/api/me/leaderboard-opt-in"), Map.of("optIn", true)), TOP))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaderboardOptIn").value(true));
        // 正式環境是交易提交後由 LeaderboardCacheListener 丟掉快取；測試跑在會回滾的交易裡，
        // 看不到 AFTER_COMMIT，所以這裡自己丟（接線本身由 LeaderboardCacheListenerTest 顧）
        leaderboard.evict();

        mvc.perform(get("/api/leaderboard"))
                .andExpect(jsonPath("$.entries[0].name").value("送最多的人"))
                .andExpect(jsonPath("$.entries[0].anonymous").value(false));

        mvc.perform(as(withBody(put("/api/me/leaderboard-opt-in"), Map.of("optIn", false)), TOP))
                .andExpect(status().isOk());
        leaderboard.evict();

        mvc.perform(get("/api/leaderboard"))
                .andExpect(jsonPath("$.entries[0].name").value("匿名的聖誕老人"))
                .andExpect(jsonPath("$.entries[0].delivered").value(1));   // 份數照算
    }

    @Test
    @DisplayName("limit 只回前幾名，但總數與目標份數仍是全部")
    void limitTrimsTheListOnly() throws Exception {
        deliver(TOP, "一號");
        deliver(SECOND, "二號");
        deliver(THIRD, "三號");

        mvc.perform(get("/api/leaderboard").param("limit", "2"))
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.donorCount").value(3))
                .andExpect(jsonPath("$.delivered").value(3))
                // 目標份數＝這個檔期的願望總數
                .andExpect(jsonPath("$.goal").value(3));
    }

    @Test
    @DisplayName("目標份數算這個檔期的所有願望：可認領、已認領、已完成都算，上架期結束後才核准的也算")
    void goalCountsEveryListedWish() throws Exception {
        deliver(TOP, "已送達的");                      // 已完成
        claimOnly(SECOND, listed("已被認領的"));        // 已認領
        listed("還沒人認領的");                         // 可認領

        mvc.perform(get("/api/leaderboard"))
                .andExpect(jsonPath("$.delivered").value(1))
                .andExpect(jsonPath("$.goal").value(3));

        // 上架期間縮到昨天為止：上架期結束後才被核准的願望，仍然算這個檔期的
        LocalDate yesterday = LocalDate.now(TaiwanYear.ZONE).minusDays(1);
        campaigns.update(campaign.getId(), new CampaignRequest(campaign.getName(), null,
                "衛部救字第1150000號", null, campaign.getCollectionStart(), campaign.getCollectionEnd(),
                campaign.getUseStart(), campaign.getUseEnd(), campaign.getWishListStart(), yesterday,
                campaign.getReceiptPrefix(), null, null, null));
        leaderboard.evict();

        mvc.perform(get("/api/leaderboard"))
                .andExpect(jsonPath("$.goal").value(3));
    }

    @Test
    @DisplayName("我的成就：當期名次即時反映，稱號看累計份數")
    void myAchievementIsFreshAndTierIsLifetime() throws Exception {
        mvc.perform(as(get("/api/me/leaderboard"), TOP))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ranked").value(false))
                .andExpect(jsonPath("$.lifetimeDelivered").value(0))
                .andExpect(jsonPath("$.tier").doesNotExist())
                .andExpect(jsonPath("$.nextTier").value("ELF"))
                .andExpect(jsonPath("$.toNextTier").value(1))
                .andExpect(jsonPath("$.showName").value(false));

        deliver(TOP, "一號");

        mvc.perform(as(get("/api/me/leaderboard"), TOP))
                .andExpect(jsonPath("$.ranked").value(true))
                .andExpect(jsonPath("$.delivered").value(1))
                .andExpect(jsonPath("$.rank").value(1))
                .andExpect(jsonPath("$.lifetimeDelivered").value(1))
                .andExpect(jsonPath("$.tier").value("ELF"))
                .andExpect(jsonPath("$.nextTier").value("REINDEER"))
                .andExpect(jsonPath("$.toNextTier").value(2));
    }

    @Test
    @DisplayName("結案（產生結案文件包）時記下這一屆的前三名，重跑不會留下重複或過時的紀錄")
    void podiumIsRecordedOnClosing() throws Exception {
        deliver(TOP, "一號");
        deliver(TOP, "二號");
        deliver(SECOND, "三號");

        exports.startPackage(campaign.getId(), null);
        exports.startPackage(campaign.getId(), null);        // 重跑

        mvc.perform(get("/api/leaderboard/podiums"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaigns.length()").value(1))
                .andExpect(jsonPath("$.campaigns[0].entries.length()").value(2))
                .andExpect(jsonPath("$.campaigns[0].entries[0].rank").value(1))
                .andExpect(jsonPath("$.campaigns[0].entries[0].delivered").value(2))
                .andExpect(jsonPath("$.campaigns[0].entries[0].name").value("匿名的聖誕老人"));
    }
}
