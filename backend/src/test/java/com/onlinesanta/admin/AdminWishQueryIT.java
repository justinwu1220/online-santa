package com.onlinesanta.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.onlinesanta.admin.dto.WishFreezeRequest;
import com.onlinesanta.attachment.AttachmentPurpose;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
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

/** 管理員「願望」頁的合併讀取：清單、篩選、搜尋、數量、抽屜資料與點擊載入的稽核。 */
@DisplayName("管理員願望檢視")
class AdminWishQueryIT extends ApiIntegrationTest {

    private static final String ADMIN = "platform-admin@example.com";
    private static final String ORG_USER = "adminview-org@example.org";
    private static final String DONOR = "adminview-donor@example.com";

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    UserRepository users;

    @Autowired
    WishRepository wishes;

    @Autowired
    AdminAuditLogRepository auditLogs;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    @jakarta.persistence.PersistenceContext
    jakarta.persistence.EntityManager entityManager;

    private Organization organization;

    @BeforeEach
    void setUp() {
        organization = Organization.register("檢視測試機構", "王承辦", "contact@example.org", null, "0912-345-678", "台中市測試路 1 號");
        organization.approve(null, "測試資料");
        organizations.save(organization);
        User member = User.newDonor(TestJwtSupport.uidFor(ORG_USER), ORG_USER, ORG_USER);
        member.joinOrganization(organization.getId());
        users.save(member);
        users.save(User.newDonor(TestJwtSupport.uidFor(DONOR), DONOR, "熱心民眾"));
    }

    private Wish draft(String title, String nick) {
        return Wish.draft(organization, nick, AgeRange.AGE_7_9, null, title, null, WishCategory.ART, PriceRange.UNDER_500);
    }

    private UUID published(String title, String nick) {
        Wish wish = draft(title, nick);
        wish.publish();
        return wishes.save(wish).getId();
    }

    private UUID claimed(UUID wishId) throws Exception {
        String body = mvc.perform(as(post("/api/wishes/{id}/claim", wishId), DONOR))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    @Test
    @DisplayName("清單不含草稿，依 待審核→可認領→已認領→已完成 排序，篩選與搜尋都能用")
    void listFiltersSortsAndSearches() throws Exception {
        wishes.save(draft("只是草稿", "小草"));
        Wish review = draft("等審核的跳繩", "阿翔");
        review.submitForReview();
        wishes.save(review);
        UUID open = published("可認領的書", "小雲");
        claimed(published("已認領的鞋", "小月"));

        mvc.perform(as(get("/api/admin/wishes"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.content[1].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.content[2].status").value("CLAIMED"));

        mvc.perform(as(get("/api/admin/wishes").param("filter", "OPEN"), ADMIN))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(open.toString()));

        // 搜尋：禮物、孩子暱稱、機構名稱
        mvc.perform(as(get("/api/admin/wishes").param("q", "跳繩"), ADMIN)).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(as(get("/api/admin/wishes").param("q", "小月"), ADMIN)).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(as(get("/api/admin/wishes").param("q", "檢視測試"), ADMIN)).andExpect(jsonPath("$.totalElements").value(3));

        mvc.perform(as(get("/api/admin/wishes/counts"), ADMIN))
                .andExpect(jsonPath("$.all").value(3))
                .andExpect(jsonPath("$.review").value(1))
                .andExpect(jsonPath("$.open").value(1))
                .andExpect(jsonPath("$.claimed").value(1))
                .andExpect(jsonPath("$.late").value(0))
                .andExpect(jsonPath("$.done").value(0))
                .andExpect(jsonPath("$.frozen").value(0));
    }

    @Test
    @DisplayName("逾期未寄與已凍結的篩選與徽章")
    void lateAndFrozenFilters() throws Exception {
        UUID lateWish = published("逾期的願望", "小山");
        UUID claimId = claimed(lateWish);
        entityManager.flush();
        jdbc.update("update claims set ship_deadline_at = now() - interval '3 days' where id = ?", claimId);
        entityManager.clear();
        UUID frozen = published("被凍結的願望", "小星");
        mvc.perform(as(withBody(post("/api/admin/wishes/{id}/freeze", frozen), new WishFreezeRequest("調查中，先凍結")), ADMIN))
                .andExpect(status().isOk());

        mvc.perform(as(get("/api/admin/wishes").param("filter", "LATE"), ADMIN))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].late").value(true));
        mvc.perform(as(get("/api/admin/wishes").param("filter", "FROZEN"), ADMIN))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].frozen").value(true));
        mvc.perform(as(get("/api/admin/wishes/counts"), ADMIN))
                .andExpect(jsonPath("$.late").value(1))
                .andExpect(jsonPath("$.frozen").value(1));
    }

    @Test
    @DisplayName("抽屜資料含價格與捐贈人資料，並寫一筆認領詳情稽核；沒有認領者就不寫")
    void detailExposesPriceAndAuditsDonorData() throws Exception {
        UUID wishId = published("寄出的禮物", "小星");

        mvc.perform(as(get("/api/admin/wishes/{id}", wishId), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claim").doesNotExist())
                .andExpect(jsonPath("$.organization.name").value("檢視測試機構"));
        assertThat(auditLogs.findAll()).noneMatch(log -> log.getAction() == AdminAuditAction.VIEW_CLAIM_DETAIL);

        UUID claimId = claimed(wishId);
        uploadConfirmedAttachment(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId), ShipRequests.of("郵局", "R123")), DONOR))
                .andExpect(status().isOk());

        mvc.perform(as(get("/api/admin/wishes/{id}", wishId), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claim.giftPrice").value(500))
                .andExpect(jsonPath("$.claim.donorEmail").value(DONOR))
                .andExpect(jsonPath("$.claim.trackingNumber").value("R123"));
        assertThat(auditLogs.findAll()).anyMatch(log -> log.getAction() == AdminAuditAction.VIEW_CLAIM_DETAIL);
    }

    @Test
    @DisplayName("附件是點擊載入，每次載入寫稽核；草稿與不存在的願望一律 404")
    void filesAreAuditedAndDraftsAreHidden() throws Exception {
        UUID wishId = published("有附件的願望", "小星");
        mvc.perform(as(get("/api/admin/wishes/{id}/files", wishId), ADMIN)).andExpect(status().isOk());
        assertThat(auditLogs.findAll()).anyMatch(log -> log.getAction() == AdminAuditAction.VIEW_WISH_ATTACHMENTS);

        Wish draft = wishes.save(draft("看不到的草稿", "小草"));
        mvc.perform(as(get("/api/admin/wishes/{id}", draft.getId()), ADMIN)).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/admin/wishes/{id}", UUID.randomUUID()), ADMIN)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("機構照片是點擊載入並寫稽核；非管理員一律被拒")
    void organizationPhotoIsAuditedAndAdminOnly() throws Exception {
        givenConfirmedOrganizationPhoto(organization.getId());
        mvc.perform(as(get("/api/admin/organizations/{id}/photo", organization.getId()), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").exists());
        assertThat(auditLogs.findAll()).anyMatch(log -> log.getAction() == AdminAuditAction.VIEW_ORGANIZATION_PHOTO);

        mvc.perform(as(get("/api/admin/organizations/{id}/photo", organization.getId()), ORG_USER))
                .andExpect(status().isForbidden());
        mvc.perform(as(get("/api/admin/wishes"), DONOR)).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/admin/wishes/counts"), ORG_USER)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("總覽統計帶凍結數與活動目標")
    void overviewStatsIncludeFrozenAndGoals() throws Exception {
        UUID wishId = published("要被凍結", "小星");
        mvc.perform(as(withBody(post("/api/admin/wishes/{id}/freeze", wishId), new WishFreezeRequest("調查中，先凍結")), ADMIN))
                .andExpect(status().isOk());
        mvc.perform(as(get("/api/admin/stats"), ADMIN))
                .andExpect(jsonPath("$.frozenWishes").value(1))
                .andExpect(jsonPath("$.goalWishes").value(500))
                .andExpect(jsonPath("$.goalOrganizations").value(30));
    }

    @Test
    @DisplayName("可只看特定機構的願望，篩選數量也跟著該機構")
    void filtersByOrganizationIncludingCounts() throws Exception {
        published("甲機構的願望", "小星");
        Organization other = Organization.register("另一間機構", "李承辦", "other@example.org", null, null, null);
        other.approve(null, "測試資料");
        organizations.save(other);
        Wish otherWish = Wish.draft(other, "小月", AgeRange.AGE_7_9, null, "乙機構的願望", null,
                WishCategory.ART, PriceRange.UNDER_500);
        otherWish.publish();
        wishes.save(otherWish);

        mvc.perform(as(get("/api/admin/wishes"), ADMIN)).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(as(get("/api/admin/wishes").param("organizationId", other.getId().toString()), ADMIN))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("乙機構的願望"))
                .andExpect(jsonPath("$.content[0].organizationName").value("另一間機構"));
        mvc.perform(as(get("/api/admin/wishes/counts"), ADMIN)).andExpect(jsonPath("$.all").value(2));
        mvc.perform(as(get("/api/admin/wishes/counts").param("organizationId", other.getId().toString()), ADMIN))
                .andExpect(jsonPath("$.all").value(1))
                .andExpect(jsonPath("$.open").value(1));
    }
}
