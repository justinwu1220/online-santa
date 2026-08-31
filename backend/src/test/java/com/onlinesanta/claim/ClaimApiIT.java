package com.onlinesanta.claim;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.onlinesanta.claim.dto.ReleaseRequest;
import com.onlinesanta.claim.dto.ShipRequest;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.organization.ReleasePolicy;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.TestJwtSupport;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishCategory;
import com.onlinesanta.wish.WishRepository;

@DisplayName("認領流程")
class ClaimApiIT extends ApiIntegrationTest {

    private static final String ORG_USER = "org@example.org";
    private static final String OTHER_ORG_USER = "other-org@example.org";
    private static final String DONOR = "donor@example.com";
    private static final String OTHER_DONOR = "other-donor@example.com";

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    UserRepository users;

    @Autowired
    WishRepository wishes;

    private Organization organization;

    @BeforeEach
    void setUp() {
        organization = approvedOrganization("送禮之家", ORG_USER);
        approvedOrganization("別家機構", OTHER_ORG_USER);
        users.save(User.newDonor(TestJwtSupport.uidFor(DONOR), DONOR, "熱心民眾"));
        users.save(User.newDonor(TestJwtSupport.uidFor(OTHER_DONOR), OTHER_DONOR, "另一位民眾"));
    }

    private Organization approvedOrganization(String name, String memberEmail) {
        // 電話與地址現在是必填，測試資料也照著填——捐贈者的認領視圖要靠它們
        Organization org = Organization.register(
                name, "contact@example.org", "02-1234-5678", "台北市中正區某某路 1 號", null);
        org.approve(null, "測試資料");
        organizations.save(org);

        User member = User.newDonor(TestJwtSupport.uidFor(memberEmail), memberEmail, memberEmail);
        member.joinOrganization(org.getId());
        users.save(member);
        return org;
    }

    private UUID publishedWish(String title) {
        Wish wish = Wish.draft(organization, "小星", AgeRange.AGE_7_9, "畫畫",
                title, "描述", WishCategory.ART, PriceRange.UNDER_500);
        wish.publish();
        return wishes.save(wish).getId();
    }

    private UUID claimAs(UUID wishId, String donorEmail) throws Exception {
        String body = mvc.perform(as(post("/api/wishes/{id}/claim", wishId), donorEmail))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    // ------------------------------------------------------------ 認領

    @Test
    @DisplayName("寄送地址只給認領者，願望牆上沒有")
    void exposesOrganizationAddressOnlyToTheDonorWhoClaimed() throws Exception {
        publishedWish("留在牆上的願望");
        UUID wishId = publishedWish("要被認領的願望");

        // 捐贈者要靠這兩個欄位才知道禮物該寄去哪。在此之前只能用訊息串問機構
        mvc.perform(as(post("/api/wishes/{id}/claim", wishId), DONOR))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationName").value("送禮之家"))
                .andExpect(jsonPath("$.organizationAddress").value("台北市中正區某某路 1 號"))
                .andExpect(jsonPath("$.organizationPhone").value("02-1234-5678"));

        // 公開的願望牆只有機構名稱。這一條釘住那道界線——地址若漏進公開視圖，
        // 等於把每一家合作機構的地址掛在首頁上
        mvc.perform(get("/api/wishes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].organizationAddress").doesNotExist())
                .andExpect(jsonPath("$.content[0].organizationPhone").doesNotExist());
    }

    @Test
    @DisplayName("認領成功後願望轉為 CLAIMED，且不再出現在願望牆")
    void claimingRemovesWishFromTheWall() throws Exception {
        UUID wishId = publishedWish("恐龍玩具");

        mvc.perform(as(post("/api/wishes/{id}/claim", wishId), DONOR))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CLAIMED"))
                .andExpect(jsonPath("$.wishTitle").value("恐龍玩具"))
                .andExpect(jsonPath("$.organizationName").value("送禮之家"))
                .andExpect(jsonPath("$.shipDeadlineAt").exists())
                .andExpect(jsonPath("$.overdue").value(false));

        mvc.perform(get("/api/wishes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("重複認領同一願望回 409")
    void claimingAnAlreadyClaimedWishConflicts() throws Exception {
        UUID wishId = publishedWish("只有一份");
        claimAs(wishId, DONOR);

        mvc.perform(as(post("/api/wishes/{id}/claim", wishId), OTHER_DONOR))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WISH_ALREADY_CLAIMED"));
    }

    @Test
    @DisplayName("草稿無法被認領")
    void draftWishCannotBeClaimed() throws Exception {
        Wish draft = wishes.save(Wish.draft(organization, "小星", AgeRange.AGE_7_9, null,
                "草稿", null, WishCategory.TOY, PriceRange.UNDER_500));

        mvc.perform(as(post("/api/wishes/{id}/claim", draft.getId()), DONOR))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WISH_ALREADY_CLAIMED"));
    }

    @Test
    @DisplayName("未登入無法認領")
    void anonymousCannotClaim() throws Exception {
        UUID wishId = publishedWish("需要登入");

        mvc.perform(post("/api/wishes/{id}/claim", wishId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("超過同時認領上限時擋下")
    void enforcesActiveClaimQuotaPerDonor() throws Exception {
        // application.yml 設定的上限是 3
        for (int i = 0; i < 3; i++) {
            claimAs(publishedWish("願望 " + i), DONOR);
        }

        mvc.perform(as(post("/api/wishes/{id}/claim", publishedWish("第四個")), DONOR))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CLAIM_QUOTA_EXCEEDED"));
    }

    @Test
    @DisplayName("認領期限依機構的釋回設定計算並在當下快照")
    void snapshotsOrganizationReleasePolicyAtClaimTime() throws Exception {
        organization.updateReleasePolicy(ReleasePolicy.AUTO, 3);
        organizations.save(organization);

        UUID claimId = claimAs(publishedWish("自動釋回的願望"), DONOR);

        mvc.perform(as(get("/api/organizations/me/claims"), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].releasePolicySnapshot").value("AUTO"));

        // 機構事後改回 MANUAL，既有認領的快照不受影響
        organization.updateReleasePolicy(ReleasePolicy.MANUAL, null);
        organizations.save(organization);

        mvc.perform(as(get("/api/claims/{id}", claimId), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipDeadlineAt").exists());
    }

    // ------------------------------------------------------------ 完整流程

    @Test
    @DisplayName("認領到完成的完整流程")
    void fullLifecycleFromClaimToCompletion() throws Exception {
        UUID wishId = publishedWish("完整流程");
        UUID claimId = claimAs(wishId, DONOR);

        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                        new ShipRequest("黑貓宅急便", "TW1234567890")), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.trackingNumber").value("TW1234567890"))
                .andExpect(jsonPath("$.shippedAt").exists());

        mvc.perform(as(post("/api/organizations/me/claims/{id}/receive", claimId), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.donorName").value("熱心民眾"));

        mvc.perform(as(post("/api/organizations/me/claims/{id}/complete", claimId), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // 完成後願望轉為 FULFILLED，不會回到願望牆
        mvc.perform(get("/api/wishes"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/wishes/{id}", wishId))
                .andExpect(jsonPath("$.status").value("FULFILLED"));
    }

    @Test
    @DisplayName("認領歷程記錄每一步")
    void timelineRecordsEveryStep() throws Exception {
        UUID claimId = claimAs(publishedWish("歷程"), DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                new ShipRequest("郵局", "R123")), DONOR));
        mvc.perform(as(post("/api/organizations/me/claims/{id}/receive", claimId), ORG_USER));

        mvc.perform(as(get("/api/claims/{id}/timeline", claimId), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].eventType").value("CLAIMED"))
                .andExpect(jsonPath("$[1].eventType").value("SHIPPED"))
                .andExpect(jsonPath("$[1].note").value("郵局 R123"))
                .andExpect(jsonPath("$[2].eventType").value("RECEIVED"));
    }

    // ------------------------------------------------------------ 釋回與取消

    @Test
    @DisplayName("機構收回認領後願望重新上架")
    void releasingReturnsWishToTheWall() throws Exception {
        UUID wishId = publishedWish("會被收回");
        UUID claimId = claimAs(wishId, DONOR);

        mvc.perform(as(withBody(post("/api/organizations/me/claims/{id}/release", claimId),
                        new ReleaseRequest("聯繫不上捐贈者")), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"))
                .andExpect(jsonPath("$.releaseReason").value("聯繫不上捐贈者"));

        mvc.perform(get("/api/wishes"))
                .andExpect(jsonPath("$.totalElements").value(1));

        // 重新上架後可以被別人認領
        mvc.perform(as(post("/api/wishes/{id}/claim", wishId), OTHER_DONOR))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("捐贈者取消認領後願望重新上架")
    void cancellingReturnsWishToTheWall() throws Exception {
        UUID wishId = publishedWish("會被取消");
        UUID claimId = claimAs(wishId, DONOR);

        mvc.perform(as(withBody(post("/api/claims/{id}/cancel", claimId),
                        new ReleaseRequest("臨時有事")), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mvc.perform(get("/api/wishes"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("已寄出的認領不能被收回或取消")
    void shippedClaimCannotBeReleasedOrCancelled() throws Exception {
        UUID claimId = claimAs(publishedWish("已寄出"), DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                new ShipRequest("郵局", "R999")), DONOR)).andExpect(status().isOk());

        mvc.perform(as(withBody(post("/api/organizations/me/claims/{id}/release", claimId),
                        new ReleaseRequest("想收回")), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ILLEGAL_CLAIM_TRANSITION"));

        mvc.perform(as(withBody(post("/api/claims/{id}/cancel", claimId),
                        new ReleaseRequest("想取消")), DONOR))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ILLEGAL_CLAIM_TRANSITION"));
    }

    @Test
    @DisplayName("跳過寄送直接確認收到會被擋下")
    void cannotSkipShippingStep() throws Exception {
        UUID claimId = claimAs(publishedWish("跳步驟"), DONOR);

        mvc.perform(as(post("/api/organizations/me/claims/{id}/receive", claimId), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ILLEGAL_CLAIM_TRANSITION"));
    }

    // ------------------------------------------------------------ 權限邊界

    @Test
    @DisplayName("別的捐贈者看不到也動不了這筆認領")
    void otherDonorsCannotSeeOrActOnTheClaim() throws Exception {
        UUID claimId = claimAs(publishedWish("私人認領"), DONOR);

        mvc.perform(as(get("/api/claims/{id}", claimId), OTHER_DONOR))
                .andExpect(status().isNotFound());

        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                        new ShipRequest("郵局", "R000")), OTHER_DONOR))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("別的機構不能收回這筆認領")
    void otherOrganizationsCannotReleaseTheClaim() throws Exception {
        UUID claimId = claimAs(publishedWish("別家的認領"), DONOR);

        mvc.perform(as(withBody(post("/api/organizations/me/claims/{id}/release", claimId),
                        new ReleaseRequest("手伸太長")), OTHER_ORG_USER))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("願望所屬機構看得到認領詳情")
    void owningOrganizationCanSeeTheClaim() throws Exception {
        UUID claimId = claimAs(publishedWish("機構可見"), DONOR);

        mvc.perform(as(get("/api/claims/{id}", claimId), ORG_USER))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("捐贈者的視圖不含其他捐贈者的個資")
    void donorViewDoesNotExposeOtherDonorsInformation() throws Exception {
        UUID claimId = claimAs(publishedWish("個資檢查"), DONOR);

        mvc.perform(as(get("/api/claims/{id}", claimId), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.donorEmail").doesNotExist())
                .andExpect(jsonPath("$.donorName").doesNotExist());
    }

    @Test
    @DisplayName("我的認領清單只回自己的")
    void myClaimsListOnlyContainsOwnClaims() throws Exception {
        claimAs(publishedWish("我的"), DONOR);
        claimAs(publishedWish("別人的"), OTHER_DONOR);

        mvc.perform(as(get("/api/claims/me"), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].wishTitle").value("我的"));
    }

    @Test
    @DisplayName("機構的認領清單只回自己願望的認領")
    void organizationClaimListIsScopedToItsOwnWishes() throws Exception {
        claimAs(publishedWish("我機構的願望"), DONOR);

        mvc.perform(as(get("/api/organizations/me/claims"), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(as(get("/api/organizations/me/claims"), OTHER_ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
