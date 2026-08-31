package com.onlinesanta.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.onlinesanta.admin.AdminAuditAction;
import com.onlinesanta.admin.AdminAuditLogRepository;
import com.onlinesanta.admin.AdminAuditTargetType;
import com.onlinesanta.attachment.dto.UploadUrlRequest;
import com.onlinesanta.claim.dto.ReleaseRequest;
import com.onlinesanta.claim.dto.ShipRequest;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.storage.ObjectStorage;
import com.onlinesanta.storage.StorageBucket;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.InMemoryObjectStorage;
import com.onlinesanta.support.TestJwtSupport;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishCategory;
import com.onlinesanta.wish.WishRepository;

@DisplayName("附件刪除")
class AttachmentDeletionIT extends ApiIntegrationTest {

    private static final String ORG_USER = "del-org@example.org";
    private static final String OTHER_ORG_USER = "del-other-org@example.org";
    private static final String DONOR = "del-donor@example.com";
    private static final String OTHER_DONOR = "del-other-donor@example.com";
    private static final String ADMIN = "platform-admin@example.com";
    private static final String JPEG = "image/jpeg";

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    UserRepository users;

    @Autowired
    WishRepository wishes;

    @Autowired
    ObjectStorage storage;

    @Autowired
    AdminAuditLogRepository auditLogs;

    private InMemoryObjectStorage fakeStorage;
    private Organization organization;

    @BeforeEach
    void setUp() {
        fakeStorage = (InMemoryObjectStorage) storage;
        fakeStorage.reset();

        organization = approvedOrganization("刪除測試機構", ORG_USER);
        approvedOrganization("別家機構", OTHER_ORG_USER);
        users.save(User.newDonor(TestJwtSupport.uidFor(DONOR), DONOR, "熱心民眾"));
        users.save(User.newDonor(TestJwtSupport.uidFor(OTHER_DONOR), OTHER_DONOR, "另一位民眾"));
    }

    private Organization approvedOrganization(String name, String memberEmail) {
        Organization org = Organization.register(name, "王承辦", "contact@example.org", null, null, null);
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

    private UUID claimAs(UUID wishId, String donorEmail) throws Exception {
        String body = mvc.perform(as(post("/api/wishes/{id}/claim", wishId), donorEmail))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    private String objectNameOf(String uploadUrl, AttachmentPurpose purpose) {
        int start = uploadUrl.indexOf(purpose.prefix());
        return uploadUrl.substring(start, uploadUrl.indexOf('?'));
    }

    /** 走完三步驟：索取網址 → 模擬前端直傳 → 確認。回傳 [attachmentId, objectName]。 */
    private record Uploaded(UUID attachmentId, String objectName) {
    }

    private Uploaded uploadAs(AttachmentPurpose purpose, UUID targetId, String userEmail)
            throws Exception {
        var request = new UploadUrlRequest(purpose, targetId, JPEG, 120_000);
        String body = mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), userEmail))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var node = json.readTree(body);
        UUID attachmentId = UUID.fromString(node.get("attachmentId").asText());
        String objectName = objectNameOf(node.get("uploadUrl").asText(), purpose);

        fakeStorage.simulateUpload(purpose.bucket(), objectName, JPEG, 120_000);

        mvc.perform(as(post("/api/attachments/{id}/confirm", attachmentId), userEmail))
                .andExpect(status().isOk());
        return new Uploaded(attachmentId, objectName);
    }

    private void shipReceiveComplete(UUID claimId) throws Exception {
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                new ShipRequest("郵局", "R123")), DONOR)).andExpect(status().isOk());
        mvc.perform(as(post("/api/organizations/me/claims/{id}/receive", claimId), ORG_USER))
                .andExpect(status().isOk());
        mvc.perform(as(post("/api/organizations/me/claims/{id}/complete", claimId), ORG_USER))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------ 授權矩陣

    @Test
    @DisplayName("捐贈者可以刪除自己的寄送證明，檔案與紀錄都會消失")
    void donorCanDeleteOwnShippingProof() throws Exception {
        UUID claimId = claimAs(publishedWish("寄送證明"), DONOR);
        Uploaded uploaded = uploadAs(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);

        assertThat(fakeStorage.exists(AttachmentPurpose.SHIPPING_PROOF.bucket(), uploaded.objectName()))
                .isTrue();

        mvc.perform(as(delete("/api/attachments/{id}", uploaded.attachmentId()), DONOR))
                .andExpect(status().isNoContent());

        assertThat(fakeStorage.exists(AttachmentPurpose.SHIPPING_PROOF.bucket(), uploaded.objectName()))
                .as("儲存端的物件也要一併刪掉")
                .isFalse();
        mvc.perform(as(get("/api/claims/{id}/attachments", claimId), DONOR))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("別的捐贈者不能刪除這筆認領的寄送證明")
    void otherDonorsCannotDeleteShippingProof() throws Exception {
        UUID claimId = claimAs(publishedWish("不是你的證明"), DONOR);
        Uploaded uploaded = uploadAs(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);

        mvc.perform(as(delete("/api/attachments/{id}", uploaded.attachmentId()), OTHER_DONOR))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("機構不能刪除寄送證明——那是捐贈者上傳的")
    void organizationsCannotDeleteShippingProof() throws Exception {
        UUID claimId = claimAs(publishedWish("機構不能刪"), DONOR);
        Uploaded uploaded = uploadAs(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);

        mvc.perform(as(delete("/api/attachments/{id}", uploaded.attachmentId()), ORG_USER))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("機構可以刪除自己上傳的回饋照片")
    void organizationCanDeleteOwnFeedbackPhoto() throws Exception {
        UUID claimId = claimAs(publishedWish("回饋照片"), DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                new ShipRequest("郵局", "R123")), DONOR)).andExpect(status().isOk());
        mvc.perform(as(post("/api/organizations/me/claims/{id}/receive", claimId), ORG_USER))
                .andExpect(status().isOk());

        Uploaded uploaded = uploadAs(AttachmentPurpose.ORG_FEEDBACK, claimId, ORG_USER);

        mvc.perform(as(delete("/api/attachments/{id}", uploaded.attachmentId()), ORG_USER))
                .andExpect(status().isNoContent());

        assertThat(fakeStorage.exists(AttachmentPurpose.ORG_FEEDBACK.bucket(), uploaded.objectName()))
                .isFalse();
    }

    @Test
    @DisplayName("別家機構不能刪除這筆認領的回饋照片")
    void otherOrganizationsCannotDeleteFeedbackPhoto() throws Exception {
        UUID claimId = claimAs(publishedWish("不是你家的回饋"), DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                new ShipRequest("郵局", "R123")), DONOR)).andExpect(status().isOk());
        mvc.perform(as(post("/api/organizations/me/claims/{id}/receive", claimId), ORG_USER))
                .andExpect(status().isOk());
        Uploaded uploaded = uploadAs(AttachmentPurpose.ORG_FEEDBACK, claimId, ORG_USER);

        mvc.perform(as(delete("/api/attachments/{id}", uploaded.attachmentId()), OTHER_ORG_USER))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("捐贈者不能刪除回饋照片——那是機構上傳的")
    void donorsCannotDeleteFeedbackPhoto() throws Exception {
        UUID claimId = claimAs(publishedWish("捐贈者不能刪回饋"), DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                new ShipRequest("郵局", "R123")), DONOR)).andExpect(status().isOk());
        mvc.perform(as(post("/api/organizations/me/claims/{id}/receive", claimId), ORG_USER))
                .andExpect(status().isOk());
        Uploaded uploaded = uploadAs(AttachmentPurpose.ORG_FEEDBACK, claimId, ORG_USER);

        mvc.perform(as(delete("/api/attachments/{id}", uploaded.attachmentId()), DONOR))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_ORG_MEMBER"));
    }

    @Test
    @DisplayName("願望示意圖不支援用這支端點刪除")
    void wishImageCannotBeDeletedThroughThisEndpoint() throws Exception {
        UUID wishId = wishes.save(Wish.draft(organization, "小星", AgeRange.AGE_7_9, "畫畫",
                "有示意圖的願望", "描述", WishCategory.ART, PriceRange.UNDER_500)).getId();
        Uploaded uploaded = uploadAs(AttachmentPurpose.WISH_IMAGE, wishId, ORG_USER);

        mvc.perform(as(delete("/api/attachments/{id}", uploaded.attachmentId()), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ATTACHMENT_NOT_DELETABLE"));
    }

    // ------------------------------------------------------------ 管理員與稽核

    @Test
    @DisplayName("管理員可以刪除寄送證明與回饋照片，兩者都會寫稽核")
    void adminCanDeleteEitherPurposeAndBothAreAudited() throws Exception {
        UUID claimId = claimAs(publishedWish("管理員處置"), DONOR);
        Uploaded proof = uploadAs(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);

        mvc.perform(as(delete("/api/attachments/{id}", proof.attachmentId()), ADMIN))
                .andExpect(status().isNoContent());

        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                new ShipRequest("郵局", "R123")), DONOR)).andExpect(status().isOk());
        mvc.perform(as(post("/api/organizations/me/claims/{id}/receive", claimId), ORG_USER))
                .andExpect(status().isOk());
        Uploaded feedback = uploadAs(AttachmentPurpose.ORG_FEEDBACK, claimId, ORG_USER);

        mvc.perform(as(delete("/api/attachments/{id}", feedback.attachmentId()), ADMIN))
                .andExpect(status().isNoContent());

        var records = auditLogs.findAll();
        assertThat(records).hasSize(2);
        assertThat(records).allSatisfy(log -> {
            assertThat(log.getAction()).isEqualTo(AdminAuditAction.DELETE_ATTACHMENT);
            assertThat(log.getTargetType()).isEqualTo(AdminAuditTargetType.CLAIM);
            assertThat(log.getTargetId()).isEqualTo(claimId);
        });
    }

    @Test
    @DisplayName("非本人也非管理員的一般民眾刪不了別人的附件")
    void unrelatedPeopleCannotDeleteAttachments() throws Exception {
        UUID claimId = claimAs(publishedWish("無關的人"), DONOR);
        Uploaded uploaded = uploadAs(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);

        mvc.perform(delete("/api/attachments/{id}", uploaded.attachmentId()))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ terminal 狀態與冪等

    @Test
    @DisplayName("認領已經完成，捐贈者仍然能刪除寄送證明——隱私優先於封存完整性")
    void canDeleteAfterClaimReachedTerminalState() throws Exception {
        UUID claimId = claimAs(publishedWish("已完成的認領"), DONOR);
        Uploaded uploaded = uploadAs(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);
        shipReceiveComplete(claimId);

        mvc.perform(as(delete("/api/attachments/{id}", uploaded.attachmentId()), DONOR))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("已收回的認領，捐贈者仍然能刪除寄送證明")
    void canDeleteAfterClaimWasReleased() throws Exception {
        UUID claimId = claimAs(publishedWish("已收回的認領"), DONOR);
        Uploaded uploaded = uploadAs(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);

        mvc.perform(as(withBody(post("/api/organizations/me/claims/{id}/release", claimId),
                        new ReleaseRequest("逾期未寄送")), ORG_USER))
                .andExpect(status().isOk());

        mvc.perform(as(delete("/api/attachments/{id}", uploaded.attachmentId()), DONOR))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("刪除不存在的附件回 404；重複刪除同一個附件第二次也回 404 而非出錯")
    void deletingTwiceOrDeletingNothingReturnsNotFound() throws Exception {
        mvc.perform(as(delete("/api/attachments/{id}", UUID.randomUUID()), DONOR))
                .andExpect(status().isNotFound());

        UUID claimId = claimAs(publishedWish("刪兩次"), DONOR);
        Uploaded uploaded = uploadAs(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);

        mvc.perform(as(delete("/api/attachments/{id}", uploaded.attachmentId()), DONOR))
                .andExpect(status().isNoContent());
        mvc.perform(as(delete("/api/attachments/{id}", uploaded.attachmentId()), DONOR))
                .andExpect(status().isNotFound());
    }
}
