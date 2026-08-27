package com.onlinesanta.message;

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
import com.onlinesanta.message.dto.SendMessageRequest;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.TestJwtSupport;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishCategory;
import com.onlinesanta.wish.WishRepository;

@DisplayName("認領內的對話")
class MessageApiIT extends ApiIntegrationTest {

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
    private UUID claimId;

    @BeforeEach
    void setUp() throws Exception {
        organization = approvedOrganization("送禮之家", ORG_USER);
        approvedOrganization("別家機構", OTHER_ORG_USER);
        users.save(User.newDonor(TestJwtSupport.uidFor(DONOR), DONOR, "熱心民眾"));
        users.save(User.newDonor(TestJwtSupport.uidFor(OTHER_DONOR), OTHER_DONOR, "另一位民眾"));
        claimId = claim(publishedWish("對話用的願望"), DONOR);
    }

    private Organization approvedOrganization(String name, String memberEmail) {
        Organization org = Organization.register(name, "contact@example.org", null, null, null);
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

    private UUID claim(UUID wishId, String donorEmail) throws Exception {
        String body = mvc.perform(as(post("/api/wishes/{id}/claim", wishId), donorEmail))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    private void send(String from, String text) throws Exception {
        mvc.perform(as(withBody(post("/api/claims/{id}/messages", claimId),
                        new SendMessageRequest(text)), from))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------ 基本對話

    @Test
    @DisplayName("雙方可以互相傳訊息")
    void bothPartiesCanExchangeMessages() throws Exception {
        send(DONOR, "禮物我已經買好了，這週會寄出");
        send(ORG_USER, "太感謝了！小星一定很開心");

        mvc.perform(as(get("/api/claims/{id}/messages", claimId), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].body").value("禮物我已經買好了，這週會寄出"))
                .andExpect(jsonPath("$[0].fromMe").value(true))
                .andExpect(jsonPath("$[1].fromMe").value(false));

        // 同一份對話，從機構那一側看 fromMe 會相反
        mvc.perform(as(get("/api/claims/{id}/messages", claimId), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fromMe").value(false))
                .andExpect(jsonPath("$[1].fromMe").value(true));
    }

    @Test
    @DisplayName("訊息不回傳寄件者的識別資訊")
    void messagesDoNotExposeSenderIdentity() throws Exception {
        send(DONOR, "測試");

        mvc.perform(as(get("/api/claims/{id}/messages", claimId), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].senderUserId").doesNotExist())
                .andExpect(jsonPath("$[0].senderEmail").doesNotExist())
                .andExpect(jsonPath("$[0].fromMe").exists());
    }

    @Test
    @DisplayName("空白訊息會被擋下")
    void rejectsBlankMessages() throws Exception {
        mvc.perform(as(withBody(post("/api/claims/{id}/messages", claimId),
                        new SendMessageRequest("   ")), DONOR))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("認領結束後無法再傳訊息")
    void cannotSendMessagesAfterTheClaimIsClosed() throws Exception {
        mvc.perform(as(withBody(post("/api/claims/{id}/cancel", claimId),
                new ReleaseRequest("臨時有事")), DONOR)).andExpect(status().isOk());

        mvc.perform(as(withBody(post("/api/claims/{id}/messages", claimId),
                        new SendMessageRequest("還想說點什麼")), DONOR))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CLAIM_CLOSED"));

        // 已經存在的對話仍看得到
        mvc.perform(as(get("/api/claims/{id}/messages", claimId), DONOR))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------ 已讀

    @Test
    @DisplayName("標記已讀只影響對方傳來的訊息")
    void markingReadOnlyAffectsTheOtherPartysMessages() throws Exception {
        send(DONOR, "我的訊息");
        send(ORG_USER, "機構的訊息");

        mvc.perform(as(post("/api/claims/{id}/messages/mark-read", claimId), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markedRead").value(1));

        // 自己傳的訊息不會被自己標成已讀
        mvc.perform(as(post("/api/claims/{id}/messages/mark-read", claimId), DONOR))
                .andExpect(jsonPath("$.markedRead").value(0));
    }

    @Test
    @DisplayName("未讀數出現在認領清單上")
    void unreadCountAppearsInClaimLists() throws Exception {
        send(ORG_USER, "機構的第一則");
        send(ORG_USER, "機構的第二則");

        mvc.perform(as(get("/api/claims/me"), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].unreadMessageCount").value(2));

        // 機構自己傳的不算自己的未讀
        mvc.perform(as(get("/api/organizations/me/claims"), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].unreadMessageCount").value(0));

        mvc.perform(as(post("/api/claims/{id}/messages/mark-read", claimId), DONOR))
                .andExpect(status().isOk());
        mvc.perform(as(get("/api/claims/me"), DONOR))
                .andExpect(jsonPath("$.content[0].unreadMessageCount").value(0));
    }

    // ------------------------------------------------------------ 權限

    @Test
    @DisplayName("無關第三方看不到也傳不了訊息")
    void outsidersCannotReadOrWrite() throws Exception {
        send(DONOR, "私人對話");

        mvc.perform(as(get("/api/claims/{id}/messages", claimId), OTHER_DONOR))
                .andExpect(status().isNotFound());
        mvc.perform(as(get("/api/claims/{id}/messages", claimId), OTHER_ORG_USER))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/claims/{id}/messages", claimId))
                .andExpect(status().isUnauthorized());

        mvc.perform(as(withBody(post("/api/claims/{id}/messages", claimId),
                        new SendMessageRequest("插話")), OTHER_DONOR))
                .andExpect(status().isNotFound());
    }
}
