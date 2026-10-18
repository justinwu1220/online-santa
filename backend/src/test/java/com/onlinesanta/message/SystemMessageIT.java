package com.onlinesanta.message;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.onlinesanta.attachment.AttachmentPurpose;
import com.onlinesanta.claim.dto.ReleaseRequest;
import com.onlinesanta.message.dto.SendMessageRequest;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.organization.ReleasePolicy;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.ShipRequests;
import com.onlinesanta.support.TestJwtSupport;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishCategory;
import com.onlinesanta.wish.WishRepository;

import jakarta.persistence.EntityManager;

@DisplayName("系統訊息、願望對話與團隊對話")
class SystemMessageIT extends ApiIntegrationTest {

    private static final String ORG_USER = "sys-org@example.org";
    private static final String OTHER_ORG_USER = "sys-other-org@example.org";
    private static final String MANUAL_ORG_USER = "sys-manual-org@example.org";
    private static final String DONOR = "sys-donor@example.com";

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    UserRepository users;

    @Autowired
    WishRepository wishes;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entityManager;

    private Organization autoOrganization;
    private Organization manualOrganization;

    @BeforeEach
    void setUp() {
        autoOrganization = organization("自動收回之家", ORG_USER, ReleasePolicy.AUTO, 3);
        organization("別家機構", OTHER_ORG_USER, ReleasePolicy.MANUAL, null);
        manualOrganization = organization("自己決定之家", MANUAL_ORG_USER, ReleasePolicy.MANUAL, null);
        users.save(User.newDonor(TestJwtSupport.uidFor(DONOR), DONOR, "熱心民眾"));
    }

    private Organization organization(String name, String memberEmail, ReleasePolicy policy, Integer days) {
        Organization org = Organization.register(name, "王承辦", "contact@example.org", null, null, null);
        org.approve(null, "測試資料");
        org.updateReleasePolicy(policy, days);
        organizations.save(org);
        User member = User.newDonor(TestJwtSupport.uidFor(memberEmail), memberEmail, memberEmail);
        member.joinOrganization(org.getId());
        users.save(member);
        return org;
    }

    private UUID publishedWish(Organization organization, String title) {
        Wish wish = Wish.draft(organization, "小星", AgeRange.AGE_7_9, "畫畫",
                title, "描述", WishCategory.ART, PriceRange.UNDER_500);
        wish.publish();
        return wishes.save(wish).getId();
    }

    private UUID claim(UUID wishId) throws Exception {
        String body = mvc.perform(as(post("/api/wishes/{id}/claim", wishId), DONOR))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    private void makeOverdue(UUID claimId) {
        entityManager.flush();
        jdbc.update("UPDATE claims SET ship_deadline_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)), claimId);
        entityManager.clear();
    }

    private void sweep() throws Exception {
        mvc.perform(as(post("/api/admin/jobs/release-expired-claims"), PLATFORM_ADMIN))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------ 認領、寄出、取消

    @Test
    @DisplayName("認領：機構收到系統通知（計入未讀），認領者只看到機構的招呼，看不到系統通知")
    void claimWritesASystemMessageForTheOrganizationAndAWelcomeForTheDonor() throws Exception {
        UUID wishId = publishedWish(autoOrganization, "有人認領");
        UUID claimId = claim(wishId);

        mvc.perform(as(get("/api/organizations/me/wishes/{id}/conversation", wishId), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimantName").value("熱心民眾"))
                .andExpect(jsonPath("$.canSend").value(true))
                .andExpect(jsonPath("$.unread").value(1))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].kind").value("SYSTEM"))
                .andExpect(jsonPath("$.messages[0].body").value(
                        org.hamcrest.Matchers.containsString("熱心民眾 認領了這個願望")))
                .andExpect(jsonPath("$.messages[1].kind").value("WELCOME"))
                .andExpect(jsonPath("$.messages[1].fromMe").value(true));

        // 認領者的對話：只有招呼，來自機構
        mvc.perform(as(get("/api/claims/{id}/messages", claimId), DONOR))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].kind").value("WELCOME"))
                .andExpect(jsonPath("$[0].fromMe").value(false))
                .andExpect(jsonPath("$[0].body").value(
                        org.hamcrest.Matchers.containsString("謝謝你認領小星的願望")));
        // 招呼不算認領者的未讀
        mvc.perform(as(get("/api/claims/{id}", claimId), DONOR))
                .andExpect(jsonPath("$.unreadMessageCount").value(0));
    }

    @Test
    @DisplayName("回報寄送與取消認領都會通知機構；切到對話分頁標記已讀後未讀歸零")
    void shipAndCancelAreAnnouncedAndReadingClearsTheUnreadCount() throws Exception {
        UUID wishId = publishedWish(autoOrganization, "寄出與取消");
        UUID claimId = claim(wishId);
        uploadConfirmedAttachment(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                ShipRequests.of("黑貓宅急便", "9000-1234-5678")), DONOR)).andExpect(status().isOk());

        mvc.perform(as(get("/api/organizations/me/wishes/{id}/conversation", wishId), ORG_USER))
                .andExpect(jsonPath("$.unread").value(2))
                .andExpect(jsonPath("$.messages[2].body").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("黑貓宅急便"),
                        org.hamcrest.Matchers.containsString("9000-1234-5678"),
                        org.hamcrest.Matchers.containsString("確認收到並完成"))));
        mvc.perform(as(get("/api/organizations/me/unread"), ORG_USER))
                .andExpect(jsonPath("$.wishes").value(2));

        mvc.perform(as(post("/api/organizations/me/wishes/{id}/conversation/read", wishId), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marked").value(2));
        mvc.perform(as(get("/api/organizations/me/wishes/{id}/conversation", wishId), ORG_USER))
                .andExpect(jsonPath("$.unread").value(0));
        mvc.perform(as(get("/api/organizations/me/unread"), ORG_USER))
                .andExpect(jsonPath("$.wishes").value(0));

        // 另一個願望：取消認領
        UUID second = publishedWish(autoOrganization, "會被取消");
        UUID secondClaim = claim(second);
        mvc.perform(as(withBody(post("/api/claims/{id}/cancel", secondClaim), ReleaseRequest.empty()), DONOR))
                .andExpect(status().isOk());
        mvc.perform(as(get("/api/organizations/me/wishes/{id}/conversation", second), ORG_USER))
                .andExpect(jsonPath("$.canSend").value(false))
                .andExpect(jsonPath("$.claimantName").doesNotExist())
                .andExpect(jsonPath("$.messages[?(@.body =~ /.*取消了認領.*/)]").exists());
    }

    @Test
    @DisplayName("機構可以在願望的對話裡傳訊息給認領者；沒有認領者時不能傳")
    void organizationCanReplyWhileThereIsAClaimant() throws Exception {
        UUID wishId = publishedWish(autoOrganization, "來回對話");

        mvc.perform(as(withBody(post("/api/organizations/me/wishes/{id}/conversation", wishId),
                new SendMessageRequest("您好")), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("NO_ACTIVE_CLAIM"));

        UUID claimId = claim(wishId);
        mvc.perform(as(withBody(post("/api/organizations/me/wishes/{id}/conversation", wishId),
                new SendMessageRequest("謝謝您認領，有問題隨時問")), ORG_USER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromMe").value(true));

        mvc.perform(as(get("/api/claims/{id}/messages", claimId), DONOR))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].fromMe").value(false))
                .andExpect(jsonPath("$[1].body").value("謝謝您認領，有問題隨時問"));
    }

    @Test
    @DisplayName("別家機構看不到這個願望的對話；認領者也不能打機構的端點")
    void conversationIsPrivateToTheOwningOrganization() throws Exception {
        UUID wishId = publishedWish(autoOrganization, "私密對話");
        claim(wishId);

        mvc.perform(as(get("/api/organizations/me/wishes/{id}/conversation", wishId), OTHER_ORG_USER))
                .andExpect(status().isNotFound());
        mvc.perform(as(post("/api/organizations/me/wishes/{id}/conversation/read", wishId), OTHER_ORG_USER))
                .andExpect(status().isNotFound());
        mvc.perform(as(get("/api/organizations/me/wishes/{id}/conversation", wishId), DONOR))
                .andExpect(status().is4xxClientError());
    }

    // ------------------------------------------------------------ 逾期

    @Test
    @DisplayName("「我自己決定」的機構：逾期時收到一則系統通知，重複掃描不會重複通知")
    void manualPolicyIsNotifiedOnlyOnceWhenTheDeadlinePasses() throws Exception {
        UUID wishId = publishedWish(manualOrganization, "逾期通知");
        UUID claimId = claim(wishId);
        makeOverdue(claimId);

        sweep();
        sweep();

        mvc.perform(as(get("/api/organizations/me/wishes/{id}/conversation", wishId), MANUAL_ORG_USER))
                .andExpect(jsonPath("$.messages.length()").value(3))
                .andExpect(jsonPath("$.messages[2].body").value(
                        org.hamcrest.Matchers.containsString("已超過寄出期限")));
    }

    @Test
    @DisplayName("自動收回的機構：系統通知說明已收回，收回後的對話只剩系統通知")
    void automaticReleaseIsAnnounced() throws Exception {
        UUID wishId = publishedWish(autoOrganization, "自動收回通知");
        UUID claimId = claim(wishId);
        makeOverdue(claimId);

        sweep();

        mvc.perform(as(get("/api/organizations/me/wishes/{id}/conversation", wishId), ORG_USER))
                .andExpect(jsonPath("$.canSend").value(false))
                .andExpect(jsonPath("$.messages[?(@.body =~ /.*系統已自動收回.*/)]").exists())
                // 已結束的那筆認領的招呼不會混進來
                .andExpect(jsonPath("$.messages[?(@.kind == 'WELCOME')]").doesNotExist());
    }

    // ------------------------------------------------------------ 審核

    @Test
    @DisplayName("管理員核准或退回願望，機構在該願望的對話收到通知（還沒有認領者也看得到）")
    void reviewDecisionsAreAnnouncedOnTheWish() throws Exception {
        Wish wish = wishes.save(Wish.draft(autoOrganization, "小星", AgeRange.AGE_7_9, "畫畫",
                "審核通知", "描述", WishCategory.ART, PriceRange.UNDER_500));
        UUID wishId = wish.getId();
        uploadConfirmedAttachment(AttachmentPurpose.WISH_LETTER, wishId, ORG_USER);
        mvc.perform(as(post("/api/wishes/{id}/submit", wishId), ORG_USER)).andExpect(status().isOk());

        mvc.perform(as(withBody(post("/api/admin/wishes/{id}/reject", wishId),
                Map.of("note", "信件照片需要重拍")), PLATFORM_ADMIN)).andExpect(status().isOk());
        mvc.perform(as(post("/api/wishes/{id}/submit", wishId), ORG_USER)).andExpect(status().isOk());
        mvc.perform(as(post("/api/admin/wishes/{id}/approve", wishId), PLATFORM_ADMIN)).andExpect(status().isOk());

        mvc.perform(as(get("/api/organizations/me/wishes/{id}/conversation", wishId), ORG_USER))
                .andExpect(jsonPath("$.canSend").value(false))
                .andExpect(jsonPath("$.unread").value(2))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].body").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("退回"),
                        org.hamcrest.Matchers.containsString("信件照片需要重拍"))))
                .andExpect(jsonPath("$.messages[1].body").value(
                        org.hamcrest.Matchers.containsString("核准")));
    }

    // ------------------------------------------------------------ 團隊對話

    @Test
    @DisplayName("與團隊的對話：新機構先收到團隊的招呼，機構可以回覆，已讀後未讀歸零")
    void teamChatStartsWithAGreetingAndSupportsReplies() throws Exception {
        String newcomer = "sys-newcomer@example.org";
        var request = new com.onlinesanta.organization.dto.OrganizationRegistrationRequest(
                "新來的機構", "王承辦", "contact@example.org", "02-1234-5678", "0912-345-678",
                "台北市中正區某路 1 號", "平日上午9:00-下午5:00", "服務失依兒童");
        mvc.perform(as(withBody(post("/api/organizations"), request), newcomer))
                .andExpect(status().isCreated());

        mvc.perform(as(get("/api/organizations/me/team-chat"), newcomer))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fromTeam").value(true))
                .andExpect(jsonPath("$[0].body").value(org.hamcrest.Matchers.containsString("資料已經收到")));
        mvc.perform(as(get("/api/organizations/me/unread"), newcomer))
                .andExpect(jsonPath("$.team").value(1));

        mvc.perform(as(withBody(post("/api/organizations/me/team-chat"),
                new SendMessageRequest("想請問審核大概多久？")), newcomer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromTeam").value(false));
        mvc.perform(as(post("/api/organizations/me/team-chat/read"), newcomer))
                .andExpect(jsonPath("$.marked").value(1));
        mvc.perform(as(get("/api/organizations/me/unread"), newcomer))
                .andExpect(jsonPath("$.team").value(0));
        mvc.perform(as(get("/api/organizations/me/team-chat"), newcomer))
                .andExpect(jsonPath("$.length()").value(2));

        // 別家機構看不到這條對話串
        mvc.perform(as(get("/api/organizations/me/team-chat"), OTHER_ORG_USER))
                .andExpect(jsonPath("$.length()").value(0));
    }
}
