package com.onlinesanta.wish;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.onlinesanta.attachment.AttachmentPurpose;
import com.onlinesanta.attachment.dto.UploadUrlRequest;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.TestJwtSupport;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;

import jakarta.persistence.EntityManager;

/**
 * 解除凍結申請可以換信件照片：新信件核准前不上牆（只有管理員審核畫面看得到），
 * 核准後取代舊信件、屏蔽清除；退回、撤回就刪除待審的新信件。
 */
@DisplayName("解除申請換信件照片")
class WishLetterRevisionIT extends ApiIntegrationTest {

    private static final String ADMIN = "platform-admin@example.com";
    private static final String ORG_USER = "rev-org@example.org";
    private static final String OTHER_ORG_USER = "rev-other-org@example.org";
    private static final String DONOR = "rev-donor@example.com";

    @Autowired OrganizationRepository organizations;
    @Autowired UserRepository users;
    @Autowired WishRepository wishes;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    private Organization organization;
    private UUID wishId;
    private UUID claimId;

    @BeforeEach
    void setUp() throws Exception {
        organization = Organization.register("換信件機構", "王承辦", "contact@example.org", null, null, null);
        organization.approve(null, "測試資料");
        organizations.save(organization);
        User member = User.newDonor(TestJwtSupport.uidFor(ORG_USER), ORG_USER, ORG_USER);
        member.joinOrganization(organization.getId());
        users.save(member);
        Organization other = Organization.register("另一間機構", "李承辦", "other@example.org", null, null, null);
        other.approve(null, "測試資料");
        organizations.save(other);
        User otherMember = User.newDonor(TestJwtSupport.uidFor(OTHER_ORG_USER), OTHER_ORG_USER, OTHER_ORG_USER);
        otherMember.joinOrganization(other.getId());
        users.save(otherMember);
        users.save(User.newDonor(TestJwtSupport.uidFor(DONOR), DONOR, "熱心民眾"));

        Wish wish = Wish.draft(organization, "小星", AgeRange.AGE_7_9, "畫畫", "色鉛筆組", "想要畫畫",
                WishCategory.ART, PriceRange.UNDER_500);
        wishId = wishes.saveAndFlush(wish).getId();
        uploadConfirmedAttachment(AttachmentPurpose.WISH_LETTER, wishId, ORG_USER);
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

    private String donorLetterUrl() throws Exception {
        String body = mvc.perform(as(get("/api/claims/{id}/wish", claimId), DONOR))
                .andReturn().getResponse().getContentAsString();
        var urls = json.readTree(body).get("letterPhotoUrls");
        return urls.size() == 0 ? null : urls.get(0).asText();
    }

    private int count(String purpose) {
        return jdbc.queryForObject("select count(*) from attachments where owner_id = ? and owner_type = ?",
                Integer.class, wishId, purpose);
    }

    private void requestUnlock(UUID letterId) throws Exception {
        mvc.perform(as(withBody(post("/api/organizations/me/wishes/{id}/unlock-request", wishId),
                        Map.of("message", "已換新的信件", "revision", Map.of("letterAttachmentId", letterId))), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("新信件在核准前不上牆；管理員審核畫面看得到新信件；核准後取代舊信件並清除屏蔽")
    void approveReplacesTheLetterAndClearsTheMask() throws Exception {
        freeze(Map.of("reason", "信件含個資", "maskLetter", true));
        String oldUrl = jdbc.queryForObject(
                "select object_name from attachments where owner_id = ? and owner_type = 'WISH_LETTER'", String.class, wishId);

        UUID revisionId = uploadConfirmedAttachment(AttachmentPurpose.WISH_LETTER_REVISION, wishId, ORG_USER);
        // 上傳新信件不影響原本的信件，也不會出現在機構、認領者的信件清單
        assertThat(count("WISH_LETTER")).isEqualTo(1);
        mvc.perform(as(get("/api/organizations/me/wishes/{id}", wishId), ORG_USER))
                .andExpect(jsonPath("$.wish.letterPhotos.length()").value(1));

        requestUnlock(revisionId);
        mvc.perform(as(get("/api/admin/wishes/{id}", wishId), ADMIN))
                .andExpect(jsonPath("$.unlockLetter.id").value(revisionId.toString()))
                .andExpect(jsonPath("$.letterPhotos.length()").value(1));
        assertThat(donorLetterUrl()).isNull();   // 仍然屏蔽中

        mvc.perform(as(withBody(post("/api/admin/wishes/{id}/unlock-request/approve", wishId), Map.of("note", "新信件沒有個資")), ADMIN))
                .andExpect(status().isOk());
        em.flush();
        em.clear();

        assertThat(count("WISH_LETTER_REVISION")).isZero();
        assertThat(count("WISH_LETTER")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select object_name from attachments where owner_id = ? and owner_type = 'WISH_LETTER'",
                String.class, wishId)).isNotEqualTo(oldUrl).contains("wish-letter-revisions");
        assertThat(wishes.findById(wishId).orElseThrow().isLetterMasked()).isFalse();
        assertThat(wishes.findById(wishId).orElseThrow().isFrozen()).isFalse();
        assertThat(donorLetterUrl()).isNotNull().contains("wish-letter-revisions");
        mvc.perform(get("/api/wishes/{id}", wishId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.letterPhotoUrls.length()").value(1))
                .andExpect(jsonPath("$.letterMasked").value(false));
    }

    @Test
    @DisplayName("退回或撤回：待審的新信件被刪除，原本的信件不變")
    void rejectAndWithdrawDiscardTheRevision() throws Exception {
        freeze(Map.of("reason", "信件含個資"));

        requestUnlock(uploadConfirmedAttachment(AttachmentPurpose.WISH_LETTER_REVISION, wishId, ORG_USER));
        mvc.perform(as(withBody(post("/api/admin/wishes/{id}/unlock-request/reject", wishId), Map.of("note", "新信件還是看得到姓名")), ADMIN))
                .andExpect(status().isOk());
        em.flush();
        em.clear();
        assertThat(count("WISH_LETTER_REVISION")).isZero();
        assertThat(count("WISH_LETTER")).isEqualTo(1);

        requestUnlock(uploadConfirmedAttachment(AttachmentPurpose.WISH_LETTER_REVISION, wishId, ORG_USER));
        mvc.perform(as(post("/api/organizations/me/wishes/{id}/unlock-request/withdraw", wishId), ORG_USER))
                .andExpect(status().isOk());
        em.flush();
        em.clear();
        assertThat(count("WISH_LETTER_REVISION")).isZero();
        assertThat(count("WISH_LETTER")).isEqualTo(1);
    }

    @Test
    @DisplayName("上傳新信件的限制：願望沒凍結不行、別間機構不行、停權機構不行；申請附不存在的圖片被擋")
    void guards() throws Exception {
        var upload = new UploadUrlRequest(AttachmentPurpose.WISH_LETTER_REVISION, wishId, "image/jpeg", 100_000);

        mvc.perform(as(withBody(post("/api/uploads/signed-url"), upload), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("LETTER_REVISION_NOT_ALLOWED"));

        freeze(Map.of("reason", "信件含個資"));
        mvc.perform(as(withBody(post("/api/uploads/signed-url"), upload), OTHER_ORG_USER))
                .andExpect(status().isNotFound());

        mvc.perform(as(withBody(post("/api/organizations/me/wishes/{id}/unlock-request", wishId),
                        Map.of("revision", Map.of("letterAttachmentId", UUID.randomUUID()))), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_LETTER_REVISION"));

        mvc.perform(as(withBody(post("/api/admin/organizations/{id}/suspend", organization.getId()),
                        Map.of("note", "疑似冒名，先停權調查")), ADMIN)).andExpect(status().isOk());
        mvc.perform(as(withBody(post("/api/uploads/signed-url"), upload), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORGANIZATION_SUSPENDED"));
    }
}
