package com.onlinesanta.message;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.onlinesanta.message.dto.SendMessageRequest;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.TestJwtSupport;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;

/** 管理員端的團隊對話：清單、未讀、回覆、已讀，機構端看得到回覆。 */
@DisplayName("管理員團隊對話")
class AdminTeamChatIT extends ApiIntegrationTest {

    private static final String ADMIN = "platform-admin@example.com";
    private static final String ORG_USER = "teamchat-org@example.org";
    private static final String DONOR = "teamchat-donor@example.com";

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    UserRepository users;

    private Organization organization;

    @BeforeEach
    void setUp() {
        organization = Organization.register("對話測試機構", "王承辦", "contact@example.org", null, null, null);
        organization.approve(null, "測試資料");
        organizations.save(organization);
        User member = User.newDonor(TestJwtSupport.uidFor(ORG_USER), ORG_USER, ORG_USER);
        member.joinOrganization(organization.getId());
        users.save(member);
        users.save(User.newDonor(TestJwtSupport.uidFor(DONOR), DONOR, "熱心民眾"));
    }

    @Test
    @DisplayName("機構來訊出現在清單並計未讀；團隊讀過後歸零；回覆機構看得到")
    void threadsUnreadReplyAndRead() throws Exception {
        mvc.perform(as(withBody(post("/api/organizations/me/team-chat"), new SendMessageRequest("禮物還沒到，想請協助")), ORG_USER))
                .andExpect(status().isCreated());

        mvc.perform(as(get("/api/admin/team-chat"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].organizationName").value("對話測試機構"))
                .andExpect(jsonPath("$[0].unread").value(1))
                .andExpect(jsonPath("$[0].lastFromTeam").value(false));
        mvc.perform(as(get("/api/admin/team-chat/unread"), ADMIN)).andExpect(jsonPath("$.unread").value(1));

        mvc.perform(as(get("/api/admin/team-chat/{id}", organization.getId()), ADMIN))
                .andExpect(jsonPath("$[0].body").value("禮物還沒到，想請協助"));
        mvc.perform(as(post("/api/admin/team-chat/{id}/read", organization.getId()), ADMIN))
                .andExpect(jsonPath("$.marked").value(1));
        mvc.perform(as(get("/api/admin/team-chat/unread"), ADMIN)).andExpect(jsonPath("$.unread").value(0));

        mvc.perform(as(withBody(post("/api/admin/team-chat/{id}", organization.getId()),
                        new SendMessageRequest("收到了，我們幫您查")), ADMIN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromTeam").value(true));

        // 機構端：看得到回覆、有 1 則未讀
        mvc.perform(as(get("/api/organizations/me/team-chat"), ORG_USER))
                .andExpect(jsonPath("$[-1:].body").value("收到了，我們幫您查"));
        mvc.perform(as(get("/api/organizations/me/unread"), ORG_USER)).andExpect(jsonPath("$.team").value(1));
    }

    @Test
    @DisplayName("只有管理員能用；不存在的機構回 404")
    void adminOnly() throws Exception {
        mvc.perform(as(get("/api/admin/team-chat"), ORG_USER)).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/admin/team-chat"), DONOR)).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/admin/team-chat/{id}", java.util.UUID.randomUUID()), ADMIN))
                .andExpect(status().isNotFound());
    }
}
