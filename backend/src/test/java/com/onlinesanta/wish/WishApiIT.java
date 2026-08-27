package com.onlinesanta.wish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.TestJwtSupport;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.wish.dto.WishRequest;

class WishApiIT extends ApiIntegrationTest {

    private static final String ORG_USER = "approved-org@example.org";
    private static final String OTHER_ORG_USER = "other-org@example.org";
    private static final String DONOR = "donor@example.com";

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    UserRepository users;

    @BeforeEach
    void setUpApprovedOrganizations() {
        // 機構審核端點要到 M3 才有，這裡直接以核准狀態建立測試資料
        createApprovedOrganization("已核准之家", ORG_USER);
        createApprovedOrganization("另一家機構", OTHER_ORG_USER);
    }

    private void createApprovedOrganization(String name, String memberEmail) {
        Organization organization = Organization.register(
                name, "contact@example.org", null, null, "測試機構");
        organization.approve(null, "測試資料");
        organizations.save(organization);

        User member = User.newDonor(TestJwtSupport.uidFor(memberEmail), memberEmail, memberEmail);
        member.joinOrganization(organization.getId());
        users.save(member);
    }

    private WishRequest wishRequest(String title) {
        return new WishRequest("小星", AgeRange.AGE_7_9, "喜歡畫畫和恐龍",
                title, "希望有一盒 48 色的色鉛筆", WishCategory.ART, PriceRange.UNDER_500);
    }

    private UUID createWish(String title) throws Exception {
        String body = mvc.perform(as(withBody(post("/api/wishes"), wishRequest(title)), ORG_USER))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    private UUID createPublishedWish(String title) throws Exception {
        UUID id = createWish(title);
        mvc.perform(as(post("/api/wishes/{id}/publish", id), ORG_USER))
                .andExpect(status().isOk());
        return id;
    }

    // ------------------------------------------------------------ 建立與狀態流轉

    @Test
    void createsWishAsDraft() throws Exception {
        mvc.perform(as(withBody(post("/api/wishes"), wishRequest("一盒色鉛筆")), ORG_USER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.title").value("一盒色鉛筆"))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.deletable").value(true))
                .andExpect(jsonPath("$.publishedAt").doesNotExist());
    }

    @Test
    void publishThenUnpublishMovesWishBetweenAvailableAndArchived() throws Exception {
        UUID id = createWish("腳踏車");

        mvc.perform(as(post("/api/wishes/{id}/publish", id), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.publishedAt").exists());

        mvc.perform(as(post("/api/wishes/{id}/unpublish", id), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        // 下架後可以重新上架
        mvc.perform(as(post("/api/wishes/{id}/publish", id), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void rejectsPublishingAnAlreadyPublishedWish() throws Exception {
        UUID id = createPublishedWish("重複上架");

        mvc.perform(as(post("/api/wishes/{id}/publish", id), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WISH_NOT_PUBLISHABLE"));
    }

    @Test
    void allowsDeletingDraftsButNotPublishedWishes() throws Exception {
        UUID draft = createWish("草稿願望");
        mvc.perform(as(delete("/api/wishes/{id}", draft), ORG_USER))
                .andExpect(status().isNoContent());

        UUID published = createPublishedWish("已公開願望");
        mvc.perform(as(delete("/api/wishes/{id}", published), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WISH_NOT_DELETABLE"));
    }

    // ------------------------------------------------------------ 權限邊界

    @Test
    void deniesWishCreationToUnapprovedOrganization() throws Exception {
        Organization pending = organizations.save(
                Organization.register("待審核之家", "pending@example.org", null, null, null));
        User member = User.newDonor(TestJwtSupport.uidFor("pending@example.org"), "pending@example.org", "待審核");
        member.joinOrganization(pending.getId());
        users.save(member);

        mvc.perform(as(withBody(post("/api/wishes"), wishRequest("不該成功")), "pending@example.org"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORGANIZATION_NOT_APPROVED"));
    }

    @Test
    void deniesWishCreationToDonors() throws Exception {
        mvc.perform(as(withBody(post("/api/wishes"), wishRequest("民眾不能建立")), DONOR))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_ORG_MEMBER"));
    }

    @Test
    void hidesOtherOrganizationsWishesBehindNotFound() throws Exception {
        UUID id = createWish("別家的草稿");

        // 回 404 而非 403：403 會洩漏「這個 id 存在」，讓人可以列舉探測
        mvc.perform(as(withBody(patch("/api/wishes/{id}", id), wishRequest("竄改")), OTHER_ORG_USER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // ------------------------------------------------------------ 公開瀏覽

    @Test
    void publicWallShowsOnlyAvailableWishes() throws Exception {
        createWish("還是草稿");
        createPublishedWish("已上架的願望");

        String body = mvc.perform(get("/api/wishes"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode content = json.readTree(body).get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("title").asText()).isEqualTo("已上架的願望");
    }

    @Test
    void publicViewExposesOrganizationNameButNoInternalFields() throws Exception {
        createPublishedWish("公開視圖檢查");

        mvc.perform(get("/api/wishes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].organizationName").value("已核准之家"))
                .andExpect(jsonPath("$.content[0].childAlias").value("小星"))
                .andExpect(jsonPath("$.content[0].ageRangeLabel").value("7-9 歲"))
                // 機構專屬欄位不得出現在公開視圖
                .andExpect(jsonPath("$.content[0].version").doesNotExist())
                .andExpect(jsonPath("$.content[0].editable").doesNotExist())
                .andExpect(jsonPath("$.content[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$.content[0].updatedAt").doesNotExist());
    }

    @Test
    void draftIsNotReachableThroughPublicDetailEndpoint() throws Exception {
        UUID draft = createWish("公開端點看不到的草稿");

        mvc.perform(get("/api/wishes/{id}", draft))
                .andExpect(status().isNotFound());
    }

    @Test
    void filtersWishesByCategory() throws Exception {
        createPublishedWish("美術用品願望");

        mvc.perform(get("/api/wishes").param("category", "ART"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(get("/api/wishes").param("category", "SPORTS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filtersWishesByAgeRangeAndPriceRange() throws Exception {
        createPublishedWish("年齡與價格篩選");

        mvc.perform(get("/api/wishes")
                        .param("ageRange", "AGE_7_9")
                        .param("priceRange", "UNDER_500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(get("/api/wishes").param("ageRange", "AGE_16_18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void exposesFilterOptionsForFrontend() throws Exception {
        mvc.perform(get("/api/wishes/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].value").value("TOY"))
                .andExpect(jsonPath("$.categories[0].label").value("玩具"))
                .andExpect(jsonPath("$.ageRanges.length()").value(6))
                .andExpect(jsonPath("$.priceRanges.length()").value(4));
    }

    // ------------------------------------------------------------ 機構後台清單

    @Test
    void organizationConsoleListsOwnWishesIncludingDrafts() throws Exception {
        createWish("我的草稿");
        createPublishedWish("我的公開願望");

        mvc.perform(as(get("/api/organizations/me/wishes"), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mvc.perform(as(get("/api/organizations/me/wishes").param("status", "DRAFT"), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("我的草稿"));
    }

    @Test
    void organizationConsoleDoesNotLeakOtherOrganizationsWishes() throws Exception {
        createWish("我的願望");

        mvc.perform(as(get("/api/organizations/me/wishes"), OTHER_ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
