package com.onlinesanta.wish;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.onlinesanta.attachment.AttachmentPurpose;
import com.onlinesanta.closing.CampaignService;
import com.onlinesanta.closing.CampaignStatus;
import com.onlinesanta.closing.dto.CampaignRequest;
import com.onlinesanta.common.TaiwanYear;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.wish.dto.WishRequest;

@DisplayName("機構新增願望只能在檔期的上架期間內")
class WishListingGateIT extends ApiIntegrationTest {

    private static final String ORG_USER = "gate-org@example.org";
    private static final String ADMIN = "platform-admin@example.com";
    private static final LocalDate TODAY = LocalDate.now(TaiwanYear.ZONE);

    @Autowired OrganizationRepository organizations;
    @Autowired UserRepository users;
    @Autowired WishRepository wishes;
    @Autowired CampaignService campaigns;

    private Organization organization;

    @BeforeEach
    void setUp() {
        organization = Organization.register("上架關卡機構", "王承辦", "contact@example.org", null, "0912-345-678", "台中市測試路 1 號");
        organization.approve(null, "測試資料");
        organizations.save(organization);
        User member = User.newDonor(ORG_USER, ORG_USER);
        member.joinOrganization(organization.getId());
        users.save(member);
    }

    private static WishRequest wishRequest(String title) {
        return new WishRequest("小星", AgeRange.AGE_7_9, "畫畫", title, "描述", WishCategory.ART, PriceRange.UNDER_500);
    }

    private UUID campaign(String prefix, LocalDate collectionStart, LocalDate collectionEnd,
                          LocalDate listStart, LocalDate listEnd, CampaignStatus status) {
        var created = campaigns.create(new CampaignRequest("檔期" + prefix, null, "衛部救字第1150000號", null,
                collectionStart, collectionEnd, collectionStart, collectionEnd, listStart, listEnd, prefix, null, null, null));
        if (status == CampaignStatus.CLOSED) {
            campaigns.update(created.getId(), new CampaignRequest("檔期" + prefix, null, "衛部救字第1150000號", null,
                    collectionStart, collectionEnd, collectionStart, collectionEnd, listStart, listEnd, prefix, null, null,
                    CampaignStatus.CLOSED));
        }
        return created.getId();
    }

    private void expectClosed(String title) throws Exception {
        mvc.perform(as(withBody(post("/api/wishes"), wishRequest(title)), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WISH_LISTING_CLOSED"));
    }

    @Test
    @DisplayName("沒有任何檔期：不能新增願望")
    void noCampaignMeansNoNewWishes() throws Exception {
        expectClosed("沒有檔期");
        mvc.perform(as(get("/api/organizations/me/wish-listing"), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(false))
                .andExpect(jsonPath("$.windowStart").doesNotExist());
    }

    @Test
    @DisplayName("今天在進行中檔期的上架期間內：可以新增；說明端點回目前的上架期間")
    void insideTheWindowIsAllowed() throws Exception {
        campaign("OPEN", TODAY.minusDays(10), TODAY.plusDays(60), TODAY.minusDays(5), TODAY.plusDays(20), CampaignStatus.ACTIVE);

        mvc.perform(as(withBody(post("/api/wishes"), wishRequest("期間內")), ORG_USER))
                .andExpect(status().isCreated());
        mvc.perform(as(get("/api/organizations/me/wish-listing"), ORG_USER))
                .andExpect(jsonPath("$.open").value(true))
                .andExpect(jsonPath("$.windowStart").value(TODAY.minusDays(5).toString()))
                .andExpect(jsonPath("$.windowEnd").value(TODAY.plusDays(20).toString()));
    }

    @Test
    @DisplayName("上架期間還沒開始或已經結束：不能新增；尚未開始時說明端點回下一次上架期間")
    void outsideTheWindowIsRejected() throws Exception {
        campaign("LATE", TODAY.minusDays(10), TODAY.plusDays(90), TODAY.plusDays(5), TODAY.plusDays(30), CampaignStatus.ACTIVE);
        expectClosed("還沒開始");
        mvc.perform(as(get("/api/organizations/me/wish-listing"), ORG_USER))
                .andExpect(jsonPath("$.open").value(false))
                .andExpect(jsonPath("$.windowStart").value(TODAY.plusDays(5).toString()))
                .andExpect(jsonPath("$.windowEnd").value(TODAY.plusDays(30).toString()));
    }

    @Test
    @DisplayName("上架期間已過：不能新增")
    void afterTheWindowIsRejected() throws Exception {
        campaign("PAST", TODAY.minusDays(60), TODAY.plusDays(30), TODAY.minusDays(40), TODAY.minusDays(1), CampaignStatus.ACTIVE);
        expectClosed("已經結束");
    }

    @Test
    @DisplayName("已結案（CLOSED）的檔期不算，即使今天仍在它的上架期間內")
    void closedCampaignDoesNotCount() throws Exception {
        campaign("DONE", TODAY.minusDays(10), TODAY.plusDays(60), TODAY.minusDays(5), TODAY.plusDays(20), CampaignStatus.CLOSED);
        expectClosed("已結案");
    }

    @Test
    @DisplayName("不在上架期間時，既有願望仍可修改、提交審核，管理員仍可審核與凍結——只擋新增")
    void editingAndReviewAreNotRestricted() throws Exception {
        UUID openId = campaign("OPEN", TODAY.minusDays(10), TODAY.plusDays(60), TODAY.minusDays(5), TODAY.plusDays(20), CampaignStatus.ACTIVE);
        String body = mvc.perform(as(withBody(post("/api/wishes"), wishRequest("先建立")), ORG_USER))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID wishId = UUID.fromString(json.readTree(body).get("id").asText());

        // 把上架期間改成已過
        campaigns.update(openId, new CampaignRequest("檔期OPEN", null, "衛部救字第1150000號", null,
                TODAY.minusDays(10), TODAY.plusDays(60), TODAY.minusDays(10), TODAY.plusDays(60),
                TODAY.minusDays(8), TODAY.minusDays(2), "OPEN", null, null, null));
        expectClosed("現在不能新增");

        mvc.perform(as(withBody(patch("/api/wishes/{id}", wishId), wishRequest("改過的標題")), ORG_USER))
                .andExpect(status().isOk());
        uploadConfirmedAttachment(AttachmentPurpose.WISH_LETTER, wishId, ORG_USER);
        mvc.perform(as(post("/api/wishes/{id}/submit", wishId), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
        mvc.perform(as(withBody(post("/api/admin/wishes/{id}/approve", wishId),
                        new com.onlinesanta.admin.dto.ReviewDecisionRequest("可以")), ADMIN))
                .andExpect(status().isOk());
    }
}
