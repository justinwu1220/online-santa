package com.onlinesanta.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.onlinesanta.claim.dto.ReleaseRequest;
import com.onlinesanta.common.TaiwanYear;
import com.onlinesanta.support.ShipRequests;
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
        // 地址現在是必填，電話則是聯絡電話/行動電話兩者至少填一個——這裡兩支都填，
        // 讓下面的測試能同時驗證兩支都會出現在認領者視圖裡
        Organization org = Organization.register(name, "王承辦", "contact@example.org",
                "02-1234-5678", "0912-345-678", "台北市中正區某某路 1 號", null, null);
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

        // 捐贈者要靠這些欄位才知道禮物該寄去哪、怎麼聯絡機構。在此之前只能用訊息串問機構
        mvc.perform(as(post("/api/wishes/{id}/claim", wishId), DONOR))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationName").value("送禮之家"))
                // 沒填簡介時不出現這個欄位
                .andExpect(jsonPath("$.organizationDescription").doesNotExist())
                .andExpect(jsonPath("$.organizationAddress").value("台北市中正區某某路 1 號"))
                .andExpect(jsonPath("$.organizationPhone").value("02-1234-5678"))
                .andExpect(jsonPath("$.organizationMobilePhone").value("0912-345-678"));

        // 公開的願望牆只有機構名稱。這一條釘住那道界線——地址/電話若漏進公開視圖，
        // 等於把每一家合作機構的聯絡資料掛在首頁上。用 status=AVAILABLE 篩選，
        // 不受「願望牆預設也顯示已認領/已完成」這個範圍影響，只檢查欄位外洩
        mvc.perform(get("/api/wishes").param("status", "AVAILABLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].organizationAddress").doesNotExist())
                .andExpect(jsonPath("$.content[0].organizationPhone").doesNotExist())
                .andExpect(jsonPath("$.content[0].organizationMobilePhone").doesNotExist());
    }

    /**
     * 願望牆改版後，已認領／已完成的願望不會從牆上消失——牆上要讓人看到進度，
     * 只是「可認領」這個篩選值會把它們排除。見 WishApiIT 裡對這個行為的完整覆蓋，
     * 這裡只確認認領這個動作本身跟願望牆的互動符合預期。
     */
    @Test
    @DisplayName("認領成功後願望轉為 CLAIMED，願望牆預設仍顯示它，但 status=AVAILABLE 會排除")
    void claimingMovesWishToClaimedButKeepsItOnTheWall() throws Exception {
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
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("CLAIMED"));

        mvc.perform(get("/api/wishes").param("status", "AVAILABLE"))
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

        uploadConfirmedAttachment(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                        ShipRequests.of("黑貓宅急便", "TW1234567890")), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.trackingNumber").value("TW1234567890"))
                .andExpect(jsonPath("$.giftDescription").value("繪本與文具組"))
                .andExpect(jsonPath("$.giftQuantity").value(1))
                .andExpect(jsonPath("$.giftPrice").value(500))
                .andExpect(jsonPath("$.shippedOn").value(LocalDate.now(TaiwanYear.ZONE).toString()))
                .andExpect(jsonPath("$.shippedAt").exists());

        // 機構「確認收到並完成」一步走完：收到日期（沒填就是今天）與確認時間分開記錄
        completeAsOrganization(claimId, ORG_USER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.donorName").value("熱心民眾"))
                .andExpect(jsonPath("$.receivedOn").value(LocalDate.now(TaiwanYear.ZONE).toString()))
                .andExpect(jsonPath("$.receivedAt").exists())
                .andExpect(jsonPath("$.completedAt").exists())
                // 機構收貨時要能核對禮物
                .andExpect(jsonPath("$.giftDescription").value("繪本與文具組"))
                .andExpect(jsonPath("$.giftQuantity").value(1));

        // 完成後願望轉為 FULFILLED，願望牆預設仍顯示它（標示已完成），
        // 但不再算「可認領」
        mvc.perform(get("/api/wishes"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("FULFILLED"));
        mvc.perform(get("/api/wishes").param("status", "AVAILABLE"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/wishes/{id}", wishId))
                .andExpect(jsonPath("$.status").value("FULFILLED"));
    }

    @Test
    @DisplayName("沒有寄送證明不能回報已寄出")
    void shippingRequiresAtLeastOneConfirmedProofPhoto() throws Exception {
        UUID claimId = claimAs(publishedWish("還沒有證明照片"), DONOR);

        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                        ShipRequests.of("黑貓宅急便", "TW000")), DONOR))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SHIPPING_PROOF_REQUIRED"));

        uploadConfirmedAttachment(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);

        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                        ShipRequests.of("黑貓宅急便", "TW000")), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    @Test
    @DisplayName("回報寄送缺少禮物資訊或內容不合理會被拒絕")
    void shippingRejectsMissingOrInvalidGiftDetails() throws Exception {
        UUID claimId = claimAs(publishedWish("禮物資訊驗證"), DONOR);
        uploadConfirmedAttachment(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);

        // 只有物流資訊：四個新欄位都必填
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                        Map.of("carrier", "郵局", "trackingNumber", "R1")), DONOR))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.giftDescription").exists())
                .andExpect(jsonPath("$.fieldErrors.giftQuantity").exists())
                .andExpect(jsonPath("$.fieldErrors.giftPrice").exists())
                .andExpect(jsonPath("$.fieldErrors.shippedOn").exists());

        // 數量 0、價格為負、寄出日期是明天
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                        Map.of("carrier", "郵局", "trackingNumber", "R1",
                                "giftDescription", "繪本", "giftQuantity", 0,
                                "giftPrice", -1,
                                "shippedOn", LocalDate.now(TaiwanYear.ZONE).plusDays(1).toString())), DONOR))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.giftQuantity").exists())
                .andExpect(jsonPath("$.fieldErrors.giftPrice").exists())
                .andExpect(jsonPath("$.fieldErrors.shippedOn").exists());

        // 價格 0 是合法的（自家物資）
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                        Map.of("carrier", "郵局", "trackingNumber", "R1",
                                "giftDescription", "繪本", "giftQuantity", 2,
                                "giftPrice", 0,
                                "shippedOn", LocalDate.now(TaiwanYear.ZONE).toString())), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.giftQuantity").value(2))
                .andExpect(jsonPath("$.giftPrice").value(0));
    }

    @Test
    @DisplayName("認領歷程記錄每一步")
    void timelineRecordsEveryStep() throws Exception {
        UUID claimId = claimAs(publishedWish("歷程"), DONOR);
        uploadConfirmedAttachment(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                ShipRequests.of("郵局", "R123")), DONOR));
        completeAsOrganization(claimId, ORG_USER);

        mvc.perform(as(get("/api/claims/{id}/timeline", claimId), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].eventType").value("CLAIMED"))
                .andExpect(jsonPath("$[1].eventType").value("SHIPPED"))
                .andExpect(jsonPath("$[1].note").value("郵局 R123"))
                // 機構一步確認收到並完成，歷程仍然記下兩個事件
                .andExpect(jsonPath("$[2].eventType").value("RECEIVED"))
                .andExpect(jsonPath("$[3].eventType").value("COMPLETED"));
    }

    // ------------------------------------------------------------ 釋回與取消

    @Test
    @DisplayName("機構收回認領後願望重新上架")
    void releasingReturnsWishToTheWall() throws Exception {
        UUID wishId = publishedWish("會被收回");
        UUID claimId = claimAs(wishId, DONOR);

        mvc.perform(as(withBody(post("/api/organizations/me/claims/{id}/release", claimId),
                        java.util.Map.of("reason", "UNREACHABLE")), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"))
                .andExpect(jsonPath("$.releaseReason").value("認領者聯繫不上"));

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
        uploadConfirmedAttachment(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                ShipRequests.of("郵局", "R999")), DONOR)).andExpect(status().isOk());

        mvc.perform(as(withBody(post("/api/organizations/me/claims/{id}/release", claimId),
                        java.util.Map.of("reason", "OTHER", "detail", "想收回")), ORG_USER))
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

        mvc.perform(as(withBody(post("/api/organizations/me/claims/{id}/complete", claimId),
                        java.util.Map.of("inspected", true)), ORG_USER))
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
                        ShipRequests.of("郵局", "R000")), OTHER_DONOR))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("別的機構不能收回這筆認領")
    void otherOrganizationsCannotReleaseTheClaim() throws Exception {
        UUID claimId = claimAs(publishedWish("別家的認領"), DONOR);

        mvc.perform(as(withBody(post("/api/organizations/me/claims/{id}/release", claimId),
                        java.util.Map.of("reason", "OTHER")), OTHER_ORG_USER))
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
    @DisplayName("機構看得到自己的單筆認領（含回報的禮物資訊），別家機構看不到")
    void organizationCanReadItsOwnSingleClaimOnly() throws Exception {
        UUID claimId = claimAs(publishedWish("機構單筆詳情"), DONOR);
        uploadConfirmedAttachment(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                        ShipRequests.of("郵局", "R777")), DONOR))
                .andExpect(status().isOk());

        mvc.perform(as(get("/api/organizations/me/claims/{id}", claimId), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(claimId.toString()))
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.donorName").value("熱心民眾"))
                .andExpect(jsonPath("$.trackingNumber").value("R777"))
                .andExpect(jsonPath("$.giftDescription").value("繪本與文具組"))
                // 禮物價格與認領者信箱不給機構（設計稿 15.4）
                .andExpect(jsonPath("$.giftPrice").doesNotExist())
                .andExpect(jsonPath("$.donorEmail").doesNotExist());

        // 別家機構一律 404，不承認這筆認領存在
        mvc.perform(as(get("/api/organizations/me/claims/{id}", claimId), OTHER_ORG_USER))
                .andExpect(status().isNotFound());
        // 捐贈者不是機構，沒有機構身分
        mvc.perform(as(get("/api/organizations/me/claims/{id}", claimId), DONOR))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("機構端任何回應都不含禮物價格、認領者信箱與收據旗標；認領者自己看得到價格")
    void organizationSideNeverSeesPriceOrDonorContact() throws Exception {
        UUID claimId = claimAs(publishedWish("隱私規則"), DONOR);
        uploadConfirmedAttachment(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);
        uploadConfirmedAttachment(AttachmentPurpose.RECEIPT_PROOF, claimId, DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                        ShipRequests.withReceipt("郵局", "R888")), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.giftPrice").value(500))
                .andExpect(jsonPath("$.receiptRequested").value(true));

        for (String path : new String[] {
                "/api/organizations/me/claims/" + claimId,
                "/api/organizations/me/claims",
                "/api/claims/" + claimId}) {
            String body = mvc.perform(as(get(path), ORG_USER)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).as(path).doesNotContain("\"giftPrice\":").doesNotContain("donorEmail")
                    .doesNotContain(DONOR).doesNotContain("\"receiptRequested\":true");
        }

        // 認領者自己照常看得到
        mvc.perform(as(get("/api/claims/{id}", claimId), DONOR))
                .andExpect(jsonPath("$.giftPrice").value(500))
                .andExpect(jsonPath("$.receiptRequested").value(true));
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
