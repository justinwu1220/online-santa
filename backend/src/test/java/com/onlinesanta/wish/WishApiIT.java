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

    @Autowired
    WishRepository wishes;

    private UUID orgId;
    private UUID otherOrgId;

    @BeforeEach
    void setUpApprovedOrganizations() {
        // 機構審核端點要到 M3 才有，這裡直接以核准狀態建立測試資料
        orgId = createApprovedOrganization("已核准之家", ORG_USER);
        otherOrgId = createApprovedOrganization("另一家機構", OTHER_ORG_USER);
    }

    private UUID createApprovedOrganization(String name, String memberEmail) {
        Organization organization = Organization.register(
                name, "王承辦", "contact@example.org", null, null, "測試機構");
        organization.approve(null, "測試資料");
        organizations.save(organization);

        User member = User.newDonor(TestJwtSupport.uidFor(memberEmail), memberEmail, memberEmail);
        member.joinOrganization(organization.getId());
        users.save(member);
        return organization.getId();
    }

    private WishRequest wishRequest(String title) {
        return new WishRequest("小星", AgeRange.AGE_7_9, "喜歡畫畫和恐龍",
                title, "希望有一盒 48 色的色鉛筆", WishCategory.ART, PriceRange.UNDER_500);
    }

    private UUID createWish(String title) throws Exception {
        return createWish(title, ORG_USER);
    }

    private UUID createWish(String title, String asUser) throws Exception {
        String body = mvc.perform(as(withBody(post("/api/wishes"), wishRequest(title)), asUser))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    private UUID createPublishedWish(String title) throws Exception {
        return createPublishedWish(title, ORG_USER);
    }

    private UUID createPublishedWish(String title, String asUser) throws Exception {
        UUID id = createWish(title, asUser);
        mvc.perform(as(post("/api/wishes/{id}/publish", id), asUser))
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

    /**
     * 待審核的機構可以建立草稿，但不能上架。
     *
     * <p>後台的文案（「現在可以先把願望存成草稿」）承諾的就是這件事：審核期間先把
     * 內容準備好，核准後一鍵上架。把關在上架這一步，草稿不會出現在願望牆上。
     */
    @Test
    void allowsDraftCreationButNotPublishingForPendingOrganization() throws Exception {
        String pendingUser = "pending@example.org";
        Organization pending = organizations.save(
                Organization.register("待審核之家", "王承辦", pendingUser, null, null, null));
        User member = User.newDonor(TestJwtSupport.uidFor(pendingUser), pendingUser, "待審核");
        member.joinOrganization(pending.getId());
        users.save(member);

        String body = mvc.perform(
                        as(withBody(post("/api/wishes"), wishRequest("審核期間先寫好")), pendingUser))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        UUID draft = UUID.fromString(json.readTree(body).get("id").asText());

        mvc.perform(as(post("/api/wishes/{id}/publish", draft), pendingUser))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORGANIZATION_NOT_APPROVED"));
    }

    @Test
    void keepsUnapprovedOrganizationsDraftsOffTheWishWall() throws Exception {
        String pendingUser = "wall-check@example.org";
        Organization pending = organizations.save(
                Organization.register("牆上不該有的機構", "王承辦", pendingUser, null, null, null));
        User member = User.newDonor(TestJwtSupport.uidFor(pendingUser), pendingUser, "待審核");
        member.joinOrganization(pending.getId());
        users.save(member);

        mvc.perform(as(withBody(post("/api/wishes"), wishRequest("不該被公開看到")), pendingUser))
                .andExpect(status().isCreated());

        // 放寬草稿的前提是「草稿不公開」——這一條把那個前提釘住
        mvc.perform(get("/api/wishes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.title == '不該被公開看到')]").doesNotExist());
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

    /**
     * 「全部」（不帶 status）預設顯示可認領／已認領／已完成；草稿永遠不在裡面。
     *
     * <p>願望牆改版後，牆上不再只有可認領的願望——已認領／已完成也要留在牆上讓人
     * 看到進度，只是草稿與下架這兩種狀態一律不開放。
     */
    @Test
    void publicWallShowsAvailableClaimedAndFulfilledButNeverDrafts() throws Exception {
        createWish("還是草稿");
        UUID available = createPublishedWish("可認領的願望");
        UUID claimed = createPublishedWish("已認領的願望");
        wishes.markClaimed(claimed);
        UUID fulfilled = createPublishedWish("已完成的願望");
        wishes.markClaimed(fulfilled);
        wishes.markFulfilled(fulfilled);

        String body = mvc.perform(get("/api/wishes"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode content = json.readTree(body).get("content");
        assertThat(content).hasSize(3);
        assertThat(content).extracting(node -> node.get("title").asText())
                .containsExactlyInAnyOrder("可認領的願望", "已認領的願望", "已完成的願望");

        // status=AVAILABLE 可以把範圍縮小成只看可認領的
        mvc.perform(get("/api/wishes").param("status", "AVAILABLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(available.toString()));
    }

    @Test
    void filtersWishesByStatus() throws Exception {
        UUID claimed = createPublishedWish("篩選用的已認領願望");
        wishes.markClaimed(claimed);
        createPublishedWish("篩選用的可認領願望");

        mvc.perform(get("/api/wishes").param("status", "CLAIMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(claimed.toString()));

        mvc.perform(get("/api/wishes").param("status", "FULFILLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // DRAFT／ARCHIVED 不是公開可篩選的狀態，繫結階段直接 400
        mvc.perform(get("/api/wishes").param("status", "DRAFT"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void filtersWishesByOrganization() throws Exception {
        createPublishedWish("第一家機構的願望", ORG_USER);
        createPublishedWish("另一家機構的願望", OTHER_ORG_USER);

        mvc.perform(get("/api/wishes").param("organizationId", orgId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].organizationName").value("已核准之家"));

        mvc.perform(get("/api/wishes").param("organizationId", otherOrgId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].organizationName").value("另一家機構"));
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
    void filtersWishesByAgeRange() throws Exception {
        createPublishedWish("年齡篩選");

        mvc.perform(get("/api/wishes").param("ageRange", "AGE_7_9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(get("/api/wishes").param("ageRange", "AGE_16_18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    /** 願望牆拿掉了預算篩選，priceRange 不再是這個端點認得的參數——傳了也會被忽略。 */
    @Test
    void ignoresPriceRangeParamSincePublicWallNoLongerFiltersByIt() throws Exception {
        createPublishedWish("預算篩選已移除");

        mvc.perform(get("/api/wishes").param("priceRange", "OVER_2000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void exposesFilterOptionsForFrontend() throws Exception {
        mvc.perform(get("/api/wishes/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].value").value("TOY"))
                .andExpect(jsonPath("$.categories[0].label").value("玩具"))
                .andExpect(jsonPath("$.ageRanges.length()").value(6))
                .andExpect(jsonPath("$.priceRanges.length()").value(4))
                // 機構名稱選項：兩家已核准機構都要出現，不受目前有沒有願望影響
                .andExpect(jsonPath("$.organizations.length()").value(2))
                .andExpect(jsonPath("$.organizations[*].label",
                        org.hamcrest.Matchers.containsInAnyOrder("已核准之家", "另一家機構")));
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
