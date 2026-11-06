package com.onlinesanta.wish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.onlinesanta.admin.AdminAuditAction;
import com.onlinesanta.admin.AdminAuditLogRepository;
import com.onlinesanta.attachment.AttachmentPurpose;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.TestJwtSupport;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;

import jakarta.persistence.EntityManager;

/**
 * 信件照片的屏蔽與刪除：<strong>只有管理員明確屏蔽才對認領者與公開頁隱藏</strong>，
 * 凍結本身（含機構、認領者停權連動）不影響認領者看信件。
 */
@DisplayName("信件照片屏蔽與刪除")
class WishLetterMaskIT extends ApiIntegrationTest {

    private static final String ADMIN = "platform-admin@example.com";
    private static final String ORG_USER = "mask-org@example.org";
    private static final String DONOR = "mask-donor@example.com";

    @Autowired OrganizationRepository organizations;
    @Autowired UserRepository users;
    @Autowired WishRepository wishes;
    @Autowired AdminAuditLogRepository auditLogs;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    private UUID wishId;
    private UUID claimId;

    @BeforeEach
    void setUp() throws Exception {
        Organization organization = Organization.register("信件測試機構", "王承辦", "contact@example.org", null, null, null);
        organization.approve(null, "測試資料");
        organizations.save(organization);
        User member = User.newDonor(TestJwtSupport.uidFor(ORG_USER), ORG_USER, ORG_USER);
        member.joinOrganization(organization.getId());
        users.save(member);
        users.save(User.newDonor(TestJwtSupport.uidFor(DONOR), DONOR, "熱心民眾"));

        Wish wish = Wish.draft(organization, "小星", AgeRange.AGE_7_9, "畫畫", "色鉛筆組", "想要畫畫",
                WishCategory.ART, PriceRange.UNDER_500);
        wishId = wishes.saveAndFlush(wish).getId();
        uploadConfirmedAttachment(AttachmentPurpose.WISH_LETTER, wishId, ORG_USER);   // 草稿時才能上傳信件
        wish.publish();
        wishes.saveAndFlush(wish);
        String body = mvc.perform(as(post("/api/wishes/{id}/claim", wishId), DONOR))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        claimId = UUID.fromString(json.readTree(body).get("id").asText());
    }

    private void freeze(Map<String, Object> body) throws Exception {
        mvc.perform(as(withBody(patch("/api/admin/wishes/{id}/content", wishId), body), ADMIN)).andExpect(status().isOk());
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("只凍結、沒有屏蔽：認領者仍看得到信件與說明（公開頁照舊 404）；機構與管理員看得到")
    void frozenWithoutMaskKeepsLetterVisibleToTheDonor() throws Exception {
        freeze(Map.of("reason", "先凍結查看"));

        mvc.perform(get("/api/wishes/{id}", wishId)).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/claims/{id}/wish", claimId), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.letterPhotoUrls.length()").value(1))
                .andExpect(jsonPath("$.letterMasked").value(false))
                .andExpect(jsonPath("$.description").value("想要畫畫"));
        mvc.perform(as(get("/api/organizations/me/wishes/{id}", wishId), ORG_USER))
                .andExpect(jsonPath("$.wish.letterPhotos.length()").value(1))
                .andExpect(jsonPath("$.wish.letterMasked").value(false));
    }

    @Test
    @DisplayName("機構停權連動的凍結也不影響認領者看信件")
    void organizationSuspensionDoesNotHideTheLetter() throws Exception {
        mvc.perform(as(withBody(post("/api/admin/organizations/{id}/suspend",
                        wishes.findById(wishId).orElseThrow().getOrganization().getId()), Map.of("note", "疑似冒名，先停權調查")), ADMIN))
                .andExpect(status().isOk());
        em.flush();
        em.clear();

        mvc.perform(as(get("/api/claims/{id}/wish", claimId), DONOR))
                .andExpect(jsonPath("$.letterPhotoUrls.length()").value(1))
                .andExpect(jsonPath("$.letterMasked").value(false));
    }

    @Test
    @DisplayName("勾選屏蔽：認領者看不到（letterMasked=true），機構與管理員仍看得到並標示已屏蔽")
    void maskHidesTheLetterOnlyFromTheDonor() throws Exception {
        freeze(Map.of("reason", "信件露出孩子的真實姓名", "maskLetter", true));

        mvc.perform(as(get("/api/claims/{id}/wish", claimId), DONOR))
                .andExpect(jsonPath("$.letterPhotoUrls.length()").value(0))
                .andExpect(jsonPath("$.letterMasked").value(true))
                .andExpect(jsonPath("$.description").value("想要畫畫"));
        mvc.perform(as(get("/api/organizations/me/wishes/{id}", wishId), ORG_USER))
                .andExpect(jsonPath("$.wish.letterPhotos.length()").value(1))
                .andExpect(jsonPath("$.wish.letterMasked").value(true));
        mvc.perform(as(get("/api/admin/wishes/{id}", wishId), ADMIN))
                .andExpect(jsonPath("$.letterPhotos.length()").value(1))
                .andExpect(jsonPath("$.letterMasked").value(true));
        assertThat(auditLogs.findAll()).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo(AdminAuditAction.EDIT_WISH_CONTENT);
            assertThat(log.getDetail()).contains("屏蔽信件照片");
        });
    }

    @Test
    @DisplayName("已凍結後可以只屏蔽信件（不改欄位）；重複屏蔽視為沒有修改")
    void canMaskLaterAndRepeatedMaskIsNoChange() throws Exception {
        freeze(Map.of("reason", "先凍結查看"));
        mvc.perform(as(withBody(patch("/api/admin/wishes/{id}/content", wishId),
                        Map.of("reason", "查看後發現信件含個資", "freeze", false, "maskLetter", true)), ADMIN))
                .andExpect(status().isOk());
        mvc.perform(as(withBody(patch("/api/admin/wishes/{id}/content", wishId),
                        Map.of("reason", "又屏蔽一次", "freeze", false, "maskLetter", true)), ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("NO_CONTENT_CHANGES"));
    }

    @Test
    @DisplayName("解除凍結：預設維持屏蔽（願望回牆但沒有信件），勾選恢復才顯示；未凍結但被屏蔽時公開頁也看不到信件")
    void unfreezeKeepsMaskUnlessRestored() throws Exception {
        freeze(Map.of("reason", "信件含個資", "maskLetter", true));

        mvc.perform(as(post("/api/admin/wishes/{id}/unfreeze", wishId), ADMIN)).andExpect(status().isOk());
        em.flush();
        em.clear();
        mvc.perform(get("/api/wishes/{id}", wishId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.letterPhotoUrls.length()").value(0))
                .andExpect(jsonPath("$.letterMasked").value(true));

        freeze(Map.of("reason", "再凍結一次", "freeze", true));
        mvc.perform(as(withBody(post("/api/admin/wishes/{id}/unfreeze", wishId), Map.of("restoreLetter", true)), ADMIN))
                .andExpect(status().isOk());
        em.flush();
        em.clear();
        mvc.perform(get("/api/wishes/{id}", wishId))
                .andExpect(jsonPath("$.letterPhotoUrls.length()").value(1))
                .andExpect(jsonPath("$.letterMasked").value(false));
    }

    @Test
    @DisplayName("刪除信件照片：檔案與資料列都刪、寫稽核、屏蔽清除；沒有信件回 409；非管理員 403")
    void adminDeletesTheLetter() throws Exception {
        freeze(Map.of("reason", "信件含個資", "maskLetter", true));

        mvc.perform(as(withBody(delete("/api/admin/wishes/{id}/letter", wishId), Map.of("reason", "含孩子的完整姓名")), ORG_USER))
                .andExpect(status().isForbidden());
        mvc.perform(as(withBody(delete("/api/admin/wishes/{id}/letter", wishId), Map.of("reason", "x")), ADMIN))
                .andExpect(status().isBadRequest());

        mvc.perform(as(withBody(delete("/api/admin/wishes/{id}/letter", wishId), Map.of("reason", "含孩子的完整姓名")), ADMIN))
                .andExpect(status().isOk());
        em.flush();
        em.clear();

        assertThat(jdbc.queryForObject("select count(*) from attachments where owner_id = ? and owner_type = 'WISH_LETTER'",
                Integer.class, wishId)).isZero();
        assertThat(wishes.findById(wishId).orElseThrow().isLetterMasked()).isFalse();
        assertThat(auditLogs.findAll()).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo(AdminAuditAction.DELETE_WISH_LETTER);
            assertThat(log.getDetail()).contains("含孩子的完整姓名");
        });
        mvc.perform(as(get("/api/organizations/me/wishes/{id}", wishId), ORG_USER))
                .andExpect(jsonPath("$.wish.letterPhotos.length()").value(0));

        mvc.perform(as(withBody(delete("/api/admin/wishes/{id}/letter", wishId), Map.of("reason", "再刪一次")), ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("NO_LETTER_PHOTO"));
    }
}
