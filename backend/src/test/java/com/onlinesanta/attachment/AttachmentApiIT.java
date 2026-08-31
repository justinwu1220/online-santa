package com.onlinesanta.attachment;

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

import com.onlinesanta.attachment.dto.UploadUrlRequest;
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

@DisplayName("圖片上傳")
class AttachmentApiIT extends ApiIntegrationTest {

    private static final String ORG_USER = "org@example.org";
    private static final String OTHER_ORG_USER = "other-org@example.org";
    private static final String DONOR = "donor@example.com";
    private static final String OTHER_DONOR = "other-donor@example.com";
    private static final String JPEG = "image/jpeg";

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    UserRepository users;

    @Autowired
    WishRepository wishes;

    @Autowired
    ObjectStorage storage;

    private InMemoryObjectStorage fakeStorage;
    private Organization organization;

    @BeforeEach
    void setUp() {
        fakeStorage = (InMemoryObjectStorage) storage;
        fakeStorage.reset();

        organization = approvedOrganization("送禮之家", ORG_USER);
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

    private UUID draftWish(String title) {
        return wishes.save(Wish.draft(organization, "小星", AgeRange.AGE_7_9, "畫畫",
                title, "描述", WishCategory.ART, PriceRange.UNDER_500)).getId();
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

    /** 走完三步驟：索取網址 → 模擬前端直傳 → 確認。回傳 attachmentId。 */
    private UUID uploadAs(AttachmentPurpose purpose, UUID targetId, String userEmail)
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
        return attachmentId;
    }

    private String objectNameOf(String uploadUrl, AttachmentPurpose purpose) {
        int start = uploadUrl.indexOf(purpose.prefix());
        return uploadUrl.substring(start, uploadUrl.indexOf('?'));
    }

    // ------------------------------------------------------------ 三步驟流程

    @Test
    @DisplayName("索取網址、直傳、確認的完整流程")
    void completeThreeStepUploadFlow() throws Exception {
        UUID wishId = draftWish("有圖的願望");
        var request = new UploadUrlRequest(AttachmentPurpose.WISH_IMAGE, wishId, JPEG, 120_000);

        String body = mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachmentId").exists())
                .andExpect(jsonPath("$.uploadUrl").exists())
                .andExpect(jsonPath("$.contentType").value(JPEG))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn().getResponse().getContentAsString();

        var node = json.readTree(body);
        UUID attachmentId = UUID.fromString(node.get("attachmentId").asText());
        String objectName = objectNameOf(node.get("uploadUrl").asText(),
                AttachmentPurpose.WISH_IMAGE);

        fakeStorage.simulateUpload(StorageBucket.PUBLIC, objectName, JPEG, 120_000);

        mvc.perform(as(post("/api/attachments/{id}/confirm", attachmentId), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purpose").value("WISH_IMAGE"))
                .andExpect(jsonPath("$.sizeBytes").value(120_000))
                .andExpect(jsonPath("$.url").exists());
    }

    @Test
    @DisplayName("檔案沒真的上傳就確認會被擋下")
    void confirmingWithoutAnActualUploadFails() throws Exception {
        UUID wishId = draftWish("沒傳成功");
        var request = new UploadUrlRequest(AttachmentPurpose.WISH_IMAGE, wishId, JPEG, 120_000);

        String body = mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), ORG_USER))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID attachmentId = UUID.fromString(json.readTree(body).get("attachmentId").asText());

        // 拿到網址不等於檔案已上傳
        mvc.perform(as(post("/api/attachments/{id}/confirm", attachmentId), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("UPLOAD_NOT_FOUND"));
    }

    @Test
    @DisplayName("以儲存端的實際大小為準，不採信前端宣稱的值")
    void trustsTheStorageBackendOverTheClientClaim() throws Exception {
        UUID wishId = draftWish("謊報大小");
        var request = new UploadUrlRequest(AttachmentPurpose.WISH_IMAGE, wishId, JPEG, 1_000);

        String body = mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), ORG_USER))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var node = json.readTree(body);
        UUID attachmentId = UUID.fromString(node.get("attachmentId").asText());
        String objectName = objectNameOf(node.get("uploadUrl").asText(),
                AttachmentPurpose.WISH_IMAGE);

        // 宣稱 1KB，實際放上去 50MB
        fakeStorage.simulateUpload(StorageBucket.PUBLIC, objectName, JPEG, 50L * 1024 * 1024);

        mvc.perform(as(post("/api/attachments/{id}/confirm", attachmentId), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("FILE_TOO_LARGE"));
    }

    @Test
    @DisplayName("重複確認會被擋下")
    void cannotConfirmTwice() throws Exception {
        UUID wishId = draftWish("重複確認");
        UUID attachmentId = uploadAs(AttachmentPurpose.WISH_IMAGE, wishId, ORG_USER);

        mvc.perform(as(post("/api/attachments/{id}/confirm", attachmentId), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ATTACHMENT_ALREADY_CONFIRMED"));
    }

    // ------------------------------------------------------------ 檔案限制

    @Test
    @DisplayName("拒絕不支援的檔案型別")
    void rejectsUnsupportedContentTypes() throws Exception {
        UUID wishId = draftWish("不支援的型別");

        for (String contentType : new String[]{"image/svg+xml", "application/pdf", "text/html"}) {
            var request = new UploadUrlRequest(
                    AttachmentPurpose.WISH_IMAGE, wishId, contentType, 1_000);
            mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), ORG_USER))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_CONTENT_TYPE"));
        }
    }

    @Test
    @DisplayName("超過大小上限在索取網址時就擋下")
    void rejectsOversizedFilesUpFront() throws Exception {
        UUID wishId = draftWish("太大的檔案");
        var request = new UploadUrlRequest(
                AttachmentPurpose.WISH_IMAGE, wishId, JPEG, 10L * 1024 * 1024);

        mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("FILE_TOO_LARGE"));
    }

    @Test
    @DisplayName("寄送證明有數量上限")
    void enforcesAttachmentCountLimit() throws Exception {
        UUID claimId = claimAs(publishedWish("多張證明"), DONOR);

        for (int i = 0; i < AttachmentPurpose.SHIPPING_PROOF.maxPerOwner(); i++) {
            uploadAs(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);
        }

        var request = new UploadUrlRequest(
                AttachmentPurpose.SHIPPING_PROOF, claimId, JPEG, 1_000);
        mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), DONOR))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ATTACHMENT_LIMIT_REACHED"));
    }

    // ------------------------------------------------------------ 上傳授權

    @Test
    @DisplayName("別的機構不能替這個願望上傳示意圖")
    void otherOrganizationsCannotUploadWishImages() throws Exception {
        UUID wishId = draftWish("別家的願望");
        var request = new UploadUrlRequest(AttachmentPurpose.WISH_IMAGE, wishId, JPEG, 1_000);

        mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), OTHER_ORG_USER))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("民眾不能上傳願望示意圖")
    void donorsCannotUploadWishImages() throws Exception {
        UUID wishId = draftWish("民眾不能傳");
        var request = new UploadUrlRequest(AttachmentPurpose.WISH_IMAGE, wishId, JPEG, 1_000);

        mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), DONOR))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_ORG_MEMBER"));
    }

    @Test
    @DisplayName("別的捐贈者不能替這筆認領上傳寄送證明")
    void otherDonorsCannotUploadShippingProof() throws Exception {
        UUID claimId = claimAs(publishedWish("我的認領"), DONOR);
        var request = new UploadUrlRequest(AttachmentPurpose.SHIPPING_PROOF, claimId, JPEG, 1_000);

        mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), OTHER_DONOR))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("機構不能冒充捐贈者上傳寄送證明")
    void organizationsCannotUploadShippingProof() throws Exception {
        UUID claimId = claimAs(publishedWish("機構不能傳"), DONOR);
        var request = new UploadUrlRequest(AttachmentPurpose.SHIPPING_PROOF, claimId, JPEG, 1_000);

        mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), ORG_USER))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("尚未收到禮物就上傳回饋照片會被擋下")
    void feedbackPhotoRequiresTheGiftToHaveArrived() throws Exception {
        UUID claimId = claimAs(publishedWish("還沒收到"), DONOR);
        var request = new UploadUrlRequest(AttachmentPurpose.ORG_FEEDBACK, claimId, JPEG, 1_000);

        mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CLAIM_STATE_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("已進入認領流程的願望不能更換示意圖")
    void claimedWishImageIsFrozen() throws Exception {
        UUID wishId = publishedWish("已被認領");
        claimAs(wishId, DONOR);

        var request = new UploadUrlRequest(AttachmentPurpose.WISH_IMAGE, wishId, JPEG, 1_000);
        mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WISH_NOT_EDITABLE"));
    }

    @Test
    @DisplayName("別人的附件不能被確認")
    void cannotConfirmSomeoneElsesAttachment() throws Exception {
        UUID wishId = draftWish("別人的附件");
        var request = new UploadUrlRequest(AttachmentPurpose.WISH_IMAGE, wishId, JPEG, 1_000);

        String body = mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), ORG_USER))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID attachmentId = UUID.fromString(json.readTree(body).get("attachmentId").asText());

        mvc.perform(as(post("/api/attachments/{id}/confirm", attachmentId), OTHER_ORG_USER))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ 讀取與隱私

    @Test
    @DisplayName("願望示意圖出現在公開願望牆，且是不需簽章的固定網址")
    void wishImageAppearsOnThePublicWall() throws Exception {
        UUID wishId = draftWish("有示意圖");
        uploadAs(AttachmentPurpose.WISH_IMAGE, wishId, ORG_USER);
        mvc.perform(as(post("/api/wishes/{id}/publish", wishId), ORG_USER))
                .andExpect(status().isOk());

        mvc.perform(get("/api/wishes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].imageUrl").exists())
                // 公開 bucket 的網址不帶簽章參數
                .andExpect(jsonPath("$.content[0].imageUrl").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("signature"))));
    }

    @Test
    @DisplayName("換新示意圖時舊圖連同檔案一起汰除")
    void replacingTheWishImageRemovesTheOldFile() throws Exception {
        UUID wishId = draftWish("換圖");
        uploadAs(AttachmentPurpose.WISH_IMAGE, wishId, ORG_USER);
        assertThat(fakeStorage.size()).isOne();

        uploadAs(AttachmentPurpose.WISH_IMAGE, wishId, ORG_USER);
        assertThat(fakeStorage.size())
                .as("舊檔案應該被刪掉，不該累積")
                .isOne();
    }

    @Test
    @DisplayName("寄送證明與回饋照片的網址帶簽章")
    void privateAttachmentsAreServedThroughSignedUrls() throws Exception {
        UUID claimId = claimAs(publishedWish("私密附件"), DONOR);
        uploadAs(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);

        mvc.perform(as(get("/api/claims/{id}/attachments", claimId), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].purpose").value("SHIPPING_PROOF"))
                .andExpect(jsonPath("$[0].url").value(
                        org.hamcrest.Matchers.containsString("signature")));
    }

    @Test
    @DisplayName("認領的附件只有捐贈者與所屬機構看得到")
    void claimAttachmentsAreVisibleOnlyToThePartiesInvolved() throws Exception {
        UUID claimId = claimAs(publishedWish("隱私檢查"), DONOR);
        uploadAs(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);

        mvc.perform(as(get("/api/claims/{id}/attachments", claimId), DONOR))
                .andExpect(status().isOk());
        mvc.perform(as(get("/api/claims/{id}/attachments", claimId), ORG_USER))
                .andExpect(status().isOk());

        // 無關的第三方看不到——這些檔案可能含捐贈者姓名地址與孩童影像
        mvc.perform(as(get("/api/claims/{id}/attachments", claimId), OTHER_DONOR))
                .andExpect(status().isNotFound());
        mvc.perform(as(get("/api/claims/{id}/attachments", claimId), OTHER_ORG_USER))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/claims/{id}/attachments", claimId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("回饋照片在收到禮物後可以上傳，且不進公開端點")
    void feedbackPhotosStayOutOfPublicEndpoints() throws Exception {
        UUID wishId = publishedWish("回饋照片");
        UUID claimId = claimAs(wishId, DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                new ShipRequest("郵局", "R123")), DONOR)).andExpect(status().isOk());
        mvc.perform(as(post("/api/organizations/me/claims/{id}/receive", claimId), ORG_USER))
                .andExpect(status().isOk());

        uploadAs(AttachmentPurpose.ORG_FEEDBACK, claimId, ORG_USER);

        mvc.perform(as(get("/api/claims/{id}/attachments", claimId), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].purpose").value("ORG_FEEDBACK"));

        // 公開的願望詳情不含任何回饋照片
        mvc.perform(get("/api/wishes/{id}", wishId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachments").doesNotExist())
                .andExpect(jsonPath("$.imageUrl").doesNotExist());
    }

    @Test
    @DisplayName("未確認的附件不會出現在任何讀取端點")
    void unconfirmedAttachmentsAreInvisible() throws Exception {
        UUID claimId = claimAs(publishedWish("未完成上傳"), DONOR);
        var request = new UploadUrlRequest(AttachmentPurpose.SHIPPING_PROOF, claimId, JPEG, 1_000);

        mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), DONOR))
                .andExpect(status().isOk());

        mvc.perform(as(get("/api/claims/{id}/attachments", claimId), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
