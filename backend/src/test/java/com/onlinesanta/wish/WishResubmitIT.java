package com.onlinesanta.wish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.onlinesanta.admin.dto.WishFreezeRequest;
import com.onlinesanta.admin.dto.WishRejectRequest;
import com.onlinesanta.attachment.AttachmentPurpose;
import com.onlinesanta.attachment.dto.UploadUrlRequest;
import com.onlinesanta.message.MessageRepository;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.ShipRequests;
import com.onlinesanta.support.TestJwtSupport;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.wish.dto.WishRequest;

/** 機構修改已提交的願望並重新送審：下架、競態、凍結、恢復、審核與紀錄、認領後的收回。 */
@DisplayName("願望修改並重新送審")
class WishResubmitIT extends ApiIntegrationTest {

    private static final String ORG_USER = "resubmit-org@example.org";
    private static final String DONOR = "resubmit-donor@example.com";

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    UserRepository users;

    @Autowired
    WishRepository wishes;

    @Autowired
    WishEventRepository events;

    @Autowired
    MessageRepository messageRepository;

    private Organization organization;

    @BeforeEach
    void setUp() {
        organization = Organization.register("重審測試機構", "王承辦", "contact@example.org", null, "0912-345-678", "台中市測試路 1 號");
        organization.approve(null, "測試資料");
        organizations.save(organization);
        User member = User.newDonor(TestJwtSupport.uidFor(ORG_USER), ORG_USER, ORG_USER);
        member.joinOrganization(organization.getId());
        users.save(member);
        users.save(User.newDonor(TestJwtSupport.uidFor(DONOR), DONOR, "熱心民眾"));
    }

    private WishRequest request(String title, String nick) {
        return new WishRequest(nick, AgeRange.AGE_7_9, null, title, "想要一盒色鉛筆", null, null);
    }

    /** 走完建立、上傳信件、送審、核准，回傳已上架的願望 id。 */
    private UUID publishedWish(String title) throws Exception {
        String body = mvc.perform(as(withBody(post("/api/wishes"), request(title, "小星")), ORG_USER))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(json.readTree(body).get("id").asText());
        submitAndApproveWish(id, ORG_USER);
        return id;
    }

    private Wish wish(UUID id) {
        return wishes.findWithOrganizationById(id).orElseThrow();
    }

    private List<WishEventType> eventTypes(UUID id) {
        return events.findByWishIdOrderByCreatedAtAscIdAsc(id).stream().map(WishEvent::getEventType).toList();
    }

    private UUID claim(UUID wishId) throws Exception {
        String body = mvc.perform(as(post("/api/wishes/{id}/claim", wishId), DONOR))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    @Test
    @DisplayName("修改上架中的願望：先原子下架成草稿，願望牆與公開詳情都看不到，並留下下架紀錄")
    void beginEditTakesTheWishOffTheWall() throws Exception {
        UUID id = publishedWish("色鉛筆");
        mvc.perform(get("/api/wishes/{id}", id)).andExpect(status().isOk());

        mvc.perform(as(post("/api/wishes/{id}/begin-edit", id), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.takenDown").value(true))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.deletable").value(false));

        mvc.perform(get("/api/wishes/{id}", id)).andExpect(status().isNotFound());
        assertThat(eventTypes(id)).contains(WishEventType.TAKEN_DOWN_FOR_EDIT);
    }

    @Test
    @DisplayName("剛被認領的願望按修改：回 409 並維持已認領，內容鎖定")
    void beginEditLosesToAClaim() throws Exception {
        UUID id = publishedWish("色鉛筆");
        claim(id);

        mvc.perform(as(post("/api/wishes/{id}/begin-edit", id), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WISH_ALREADY_CLAIMED"));
        assertThat(wish(id).getStatus()).isEqualTo(WishStatus.CLAIMED);

        // 認領後直接改內容也被擋
        mvc.perform(as(withBody(patch("/api/wishes/{id}", id), request("偷偷改標題", "小星")), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WISH_NOT_EDITABLE"));
    }

    @Test
    @DisplayName("機構下架修改期間有人想認領：得到「暫時下架修改中」，不是「手慢了」")
    void claimingATakenDownWishExplainsWhy() throws Exception {
        UUID id = publishedWish("色鉛筆");
        mvc.perform(as(post("/api/wishes/{id}/begin-edit", id), ORG_USER)).andExpect(status().isOk());

        mvc.perform(as(post("/api/wishes/{id}/claim", id), DONOR))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WISH_TEMPORARILY_UNAVAILABLE"));
    }

    @Test
    @DisplayName("凍結中的願望修改：下架時自動解除凍結，並留下解除紀錄")
    void beginEditLiftsTheFreeze() throws Exception {
        UUID id = publishedWish("色鉛筆");
        mvc.perform(as(withBody(post("/api/admin/wishes/{id}/freeze", id), new WishFreezeRequest("信件照片露出真實姓名")), PLATFORM_ADMIN))
                .andExpect(status().isOk());
        assertThat(wish(id).isFrozen()).isTrue();

        mvc.perform(as(post("/api/wishes/{id}/begin-edit", id), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frozen").value(false));
        assertThat(wish(id).isFrozen()).isFalse();
        assertThat(eventTypes(id)).contains(WishEventType.FROZEN, WishEventType.TAKEN_DOWN_FOR_EDIT,
                WishEventType.FREEZE_LIFTED_BY_RESUBMIT);
    }

    @Test
    @DisplayName("恢復上架：內容完全沒改才允許；改了任何東西就要送審")
    void restoreOnlyWhenNothingChanged() throws Exception {
        UUID id = publishedWish("色鉛筆");
        mvc.perform(as(post("/api/wishes/{id}/begin-edit", id), ORG_USER)).andExpect(status().isOk());

        mvc.perform(as(get("/api/organizations/me/wishes/{id}", id), ORG_USER))
                .andExpect(jsonPath("$.wish.canRestore").value(true));
        mvc.perform(as(post("/api/wishes/{id}/restore", id), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
        assertThat(wish(id).getPublishedAt()).isNotNull();

        // 再下架、改了標題 → 不能恢復
        mvc.perform(as(post("/api/wishes/{id}/begin-edit", id), ORG_USER)).andExpect(status().isOk());
        mvc.perform(as(withBody(patch("/api/wishes/{id}", id), request("改過的標題", "小星")), ORG_USER))
                .andExpect(status().isOk());
        mvc.perform(as(get("/api/organizations/me/wishes/{id}", id), ORG_USER))
                .andExpect(jsonPath("$.wish.canRestore").value(false));
        mvc.perform(as(post("/api/wishes/{id}/restore", id), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WISH_NOT_RESTORABLE"));
    }

    @Test
    @DisplayName("重新送審：管理員看得到「重審」、與上次核准的差異與歷程；核准後重新上架且保留第一次上架時間")
    void resubmissionIsReviewedWithChangesAndHistory() throws Exception {
        UUID id = publishedWish("色鉛筆");
        var firstPublishedAt = wish(id).getPublishedAt();

        mvc.perform(as(post("/api/wishes/{id}/begin-edit", id), ORG_USER)).andExpect(status().isOk());
        mvc.perform(as(withBody(patch("/api/wishes/{id}", id), request("水彩組", "小月")), ORG_USER))
                .andExpect(status().isOk());
        mvc.perform(as(post("/api/wishes/{id}/submit", id), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));

        mvc.perform(as(get("/api/admin/wishes").param("filter", "REVIEW"), PLATFORM_ADMIN))
                .andExpect(jsonPath("$.content[0].resubmission").value(true));
        mvc.perform(as(get("/api/admin/wishes/{id}", id), PLATFORM_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resubmission").value(true))
                .andExpect(jsonPath("$.changes.fields.length()").value(2))
                .andExpect(jsonPath("$.changes.fields[0].field").value("願望標題"))
                .andExpect(jsonPath("$.changes.fields[0].before").value("色鉛筆"))
                .andExpect(jsonPath("$.changes.fields[0].after").value("水彩組"))
                .andExpect(jsonPath("$.changes.lettersChanged").value(false))
                .andExpect(jsonPath("$.history[?(@.type=='RESUBMITTED')]").isNotEmpty())
                .andExpect(jsonPath("$.history[?(@.type=='TAKEN_DOWN_FOR_EDIT')]").isNotEmpty());

        mvc.perform(as(post("/api/admin/wishes/{id}/approve", id), PLATFORM_ADMIN)).andExpect(status().isOk());
        Wish approved = wish(id);
        assertThat(approved.getStatus()).isEqualTo(WishStatus.AVAILABLE);
        // 資料庫只存到微秒（會四捨五入），所以容許 1 毫秒的誤差；重點是沒有被重設成核准當下的時間
        assertThat(approved.getPublishedAt())
                .isCloseTo(firstPublishedAt, org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MILLIS));
        mvc.perform(get("/api/wishes/{id}", id)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("重審被退回：回到草稿、保持下架，機構看得到原因，而且這個願望不能被刪除")
    void rejectedResubmissionStaysDraftAndCannotBeDeleted() throws Exception {
        UUID id = publishedWish("色鉛筆");
        mvc.perform(as(post("/api/wishes/{id}/begin-edit", id), ORG_USER)).andExpect(status().isOk());
        mvc.perform(as(post("/api/wishes/{id}/submit", id), ORG_USER)).andExpect(status().isOk());

        mvc.perform(as(withBody(post("/api/admin/wishes/{id}/reject", id), new WishRejectRequest("信件照片仍可辨識孩子")), PLATFORM_ADMIN))
                .andExpect(status().isOk());
        mvc.perform(as(get("/api/organizations/me/wishes/{id}", id), ORG_USER))
                .andExpect(jsonPath("$.wish.status").value("DRAFT"))
                .andExpect(jsonPath("$.wish.takenDown").value(true))
                .andExpect(jsonPath("$.wish.reviewNote").value("信件照片仍可辨識孩子"));
        mvc.perform(get("/api/wishes/{id}", id)).andExpect(status().isNotFound());

        mvc.perform(as(delete("/api/wishes/{id}", id), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WISH_NOT_DELETABLE"));
    }

    @Test
    @DisplayName("信件照片只有草稿能新增或刪除：已上架的願望不能直接換信件")
    void letterPhotosAreLockedOutsideDrafts() throws Exception {
        UUID id = publishedWish("色鉛筆");
        var request = new UploadUrlRequest(AttachmentPurpose.WISH_LETTER, id, "image/jpeg", 120_000);
        mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WISH_NOT_EDITABLE"));

        // 下架成草稿之後就可以更動
        mvc.perform(as(post("/api/wishes/{id}/begin-edit", id), ORG_USER)).andExpect(status().isOk());
        uploadConfirmedAttachment(AttachmentPurpose.WISH_LETTER, id, ORG_USER);
    }

    @Test
    @DisplayName("認領者還沒寄出：機構為修改內容收回認領，認領者在對話收到通知，願望回到牆上，可接著修改")
    void recallForEditNotifiesTheDonor() throws Exception {
        UUID id = publishedWish("色鉛筆");
        UUID claimId = claim(id);

        mvc.perform(as(withBody(post("/api/organizations/me/claims/{id}/release", claimId),
                Map.of("reason", "EDIT_WISH")), ORG_USER))
                .andExpect(status().isOk());
        assertThat(wish(id).getStatus()).isEqualTo(WishStatus.AVAILABLE);
        assertThat(eventTypes(id)).contains(WishEventType.RECALLED_FOR_EDIT);
        assertThat(messageRepository.findAll()).anyMatch(message ->
                claimId.equals(message.getClaimId()) && message.getBody().contains("需要修改願望內容"));

        mvc.perform(as(post("/api/wishes/{id}/begin-edit", id), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @DisplayName("已寄出的認領不能收回，內容也不能改")
    void shippedClaimsAreLocked() throws Exception {
        UUID id = publishedWish("色鉛筆");
        UUID claimId = claim(id);
        uploadConfirmedAttachment(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId), ShipRequests.of("郵局", "R123")), DONOR))
                .andExpect(status().isOk());

        mvc.perform(as(withBody(post("/api/organizations/me/claims/{id}/release", claimId),
                Map.of("reason", "EDIT_WISH")), ORG_USER))
                .andExpect(status().isConflict());
        mvc.perform(as(post("/api/wishes/{id}/begin-edit", id), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WISH_ALREADY_CLAIMED"));
    }
}
