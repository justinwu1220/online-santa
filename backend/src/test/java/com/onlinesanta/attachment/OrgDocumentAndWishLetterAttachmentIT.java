package com.onlinesanta.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.onlinesanta.attachment.dto.UploadUrlRequest;
import com.onlinesanta.support.ShipRequests;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.storage.ObjectStorage;
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

/**
 * 兩種較新的附件用途：機構申請文件（{@link AttachmentPurpose#ORG_DOCUMENT}）與
 * 願望信件照片（{@link AttachmentPurpose#WISH_LETTER}）。
 *
 * <p>{@link AttachmentApiIT} 已經覆蓋了三步驟流程、數量上限等共通行為，這裡只測
 * 這兩種用途各自特有的規則（格式白名單、不檢查願望狀態等）。
 */
@DisplayName("機構文件與願望信件附件")
class OrgDocumentAndWishLetterAttachmentIT extends ApiIntegrationTest {

    private static final String ADMIN = "platform-admin@example.com";
    private static final String ORG_USER = "org@example.org";
    private static final String OTHER_ORG_USER = "other-org@example.org";
    private static final String DONOR = "donor@example.com";
    private static final String JPEG = "image/jpeg";
    private static final String PDF = "application/pdf";

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

    /** 走完三步驟，content type 可自訂（圖檔或 PDF）。回傳 attachmentId。 */
    private UUID uploadAs(AttachmentPurpose purpose, UUID targetId, String contentType, String userEmail)
            throws Exception {
        return uploadAs(purpose, targetId, contentType, null, userEmail);
    }

    /** 同上，但可以附上原始檔名，用來驗證檔名有沒有正確存進去、讀出來。 */
    private UUID uploadAs(AttachmentPurpose purpose, UUID targetId, String contentType, String fileName,
            String userEmail) throws Exception {
        var request = new UploadUrlRequest(purpose, targetId, contentType, 120_000, fileName);
        String body = mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), userEmail))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var node = json.readTree(body);
        UUID attachmentId = UUID.fromString(node.get("attachmentId").asText());
        String objectName = objectNameOf(node.get("uploadUrl").asText(), purpose);

        fakeStorage.simulateUpload(purpose.bucket(), objectName, contentType, 120_000);

        mvc.perform(as(post("/api/attachments/{id}/confirm", attachmentId), userEmail))
                .andExpect(status().isOk());
        return attachmentId;
    }

    private String objectNameOf(String uploadUrl, AttachmentPurpose purpose) {
        int start = uploadUrl.indexOf(purpose.prefix());
        return uploadUrl.substring(start, uploadUrl.indexOf('?'));
    }

    // ------------------------------------------------------------ 機構文件

    @Test
    @DisplayName("機構成員可以上傳圖檔或 PDF 格式的立案文件，下載網址帶簽章")
    void organizationMemberCanUploadImageOrPdfDocuments() throws Exception {
        uploadAs(AttachmentPurpose.ORG_DOCUMENT, organization.getId(), JPEG, ORG_USER);
        uploadAs(AttachmentPurpose.ORG_DOCUMENT, organization.getId(), PDF, ORG_USER);

        mvc.perform(as(get("/api/admin/organizations/{id}/documents", organization.getId()), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].url").value(Matchers.containsString("signature")))
                .andExpect(jsonPath("$[1].contentType").value(PDF));
    }

    @Test
    @DisplayName("機構文件只接受圖檔與 PDF，其他格式一律拒絕")
    void organizationDocumentRejectsOtherContentTypes() throws Exception {
        var request = new UploadUrlRequest(
                AttachmentPurpose.ORG_DOCUMENT, organization.getId(), "text/plain", 1_000);

        mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_CONTENT_TYPE"));
    }

    @Test
    @DisplayName("別的機構不能替這個機構上傳文件")
    void otherOrganizationsCannotUploadDocuments() throws Exception {
        var request = new UploadUrlRequest(
                AttachmentPurpose.ORG_DOCUMENT, organization.getId(), JPEG, 1_000);

        mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), OTHER_ORG_USER))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("民眾不能上傳機構文件")
    void donorsCannotUploadOrganizationDocuments() throws Exception {
        var request = new UploadUrlRequest(
                AttachmentPurpose.ORG_DOCUMENT, organization.getId(), JPEG, 1_000);

        mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), DONOR))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_ORG_MEMBER"));
    }

    @Test
    @DisplayName("機構文件有數量上限")
    void organizationDocumentHasACountLimit() throws Exception {
        for (int i = 0; i < AttachmentPurpose.ORG_DOCUMENT.maxPerOwner(); i++) {
            uploadAs(AttachmentPurpose.ORG_DOCUMENT, organization.getId(), JPEG, ORG_USER);
        }

        var request = new UploadUrlRequest(
                AttachmentPurpose.ORG_DOCUMENT, organization.getId(), JPEG, 1_000);
        mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ATTACHMENT_LIMIT_REACHED"));
    }

    @Test
    @DisplayName("機構文件不支援自行刪除，請聯繫平台管理員")
    void organizationDocumentsCannotBeSelfDeleted() throws Exception {
        UUID attachmentId = uploadAs(AttachmentPurpose.ORG_DOCUMENT, organization.getId(), JPEG, ORG_USER);

        mvc.perform(as(delete("/api/attachments/{id}", attachmentId), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ATTACHMENT_NOT_DELETABLE"));
    }

    @Test
    @DisplayName("管理員可以刪除機構文件，刪除後名額釋出，機構可以立刻補傳新文件")
    void adminCanDeleteOrganizationDocumentAndFreeUpASlot() throws Exception {
        UUID first = uploadAs(AttachmentPurpose.ORG_DOCUMENT, organization.getId(), JPEG, ORG_USER);
        for (int i = 1; i < AttachmentPurpose.ORG_DOCUMENT.maxPerOwner(); i++) {
            uploadAs(AttachmentPurpose.ORG_DOCUMENT, organization.getId(), JPEG, ORG_USER);
        }

        // 名額已滿，這時機構自己傳不了
        var request = new UploadUrlRequest(AttachmentPurpose.ORG_DOCUMENT, organization.getId(), JPEG, 1_000);
        mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), ORG_USER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ATTACHMENT_LIMIT_REACHED"));

        // 管理員刪掉有問題的那份（審核發現文件不對）
        mvc.perform(as(delete("/api/attachments/{id}", first), ADMIN))
                .andExpect(status().isNoContent());

        // 名額空出來了，機構可以立刻補傳新文件
        uploadAs(AttachmentPurpose.ORG_DOCUMENT, organization.getId(), PDF, ORG_USER);

        mvc.perform(as(get("/api/organizations/me/documents"), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(AttachmentPurpose.ORG_DOCUMENT.maxPerOwner()));
    }

    @Test
    @DisplayName("機構可以在自己的後台看到自己上傳的申請文件，下載網址帶簽章")
    void organizationCanViewOwnDocuments() throws Exception {
        uploadAs(AttachmentPurpose.ORG_DOCUMENT, organization.getId(), JPEG, ORG_USER);
        uploadAs(AttachmentPurpose.ORG_DOCUMENT, organization.getId(), PDF, ORG_USER);

        mvc.perform(as(get("/api/organizations/me/documents"), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].url").value(Matchers.containsString("signature")))
                .andExpect(jsonPath("$[1].contentType").value(PDF));
    }

    @Test
    @DisplayName("文件的原始檔名會存進去，機構後台與管理員兩邊都看得到；沒給檔名時為 null")
    void documentFileNameRoundTripsToBothOwnAndAdminView() throws Exception {
        uploadAs(AttachmentPurpose.ORG_DOCUMENT, organization.getId(), PDF, "立案證書.pdf", ORG_USER);
        uploadAs(AttachmentPurpose.ORG_DOCUMENT, organization.getId(), JPEG, ORG_USER); // 沒給檔名

        mvc.perform(as(get("/api/organizations/me/documents"), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileName").value("立案證書.pdf"))
                .andExpect(jsonPath("$[1].fileName").doesNotExist());

        mvc.perform(as(get("/api/admin/organizations/{id}/documents", organization.getId()), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileName").value("立案證書.pdf"))
                .andExpect(jsonPath("$[1].fileName").doesNotExist());
    }

    @Test
    @DisplayName("機構在後台看不到別家機構的文件")
    void organizationCannotViewOtherOrganizationsDocuments() throws Exception {
        uploadAs(AttachmentPurpose.ORG_DOCUMENT, organization.getId(), JPEG, ORG_USER);

        mvc.perform(as(get("/api/organizations/me/documents"), OTHER_ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("民眾與未登入者不能查詢機構文件")
    void donorsAndAnonymousCannotViewOrganizationDocuments() throws Exception {
        mvc.perform(as(get("/api/organizations/me/documents"), DONOR))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_ORG_MEMBER"));
        mvc.perform(get("/api/organizations/me/documents"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ 願望信件

    @Test
    @DisplayName("任何願望狀態都能上傳願望信件照片，包含已完成的認領；限一張，補傳會直接取代舊照片")
    void wishLetterCanBeUploadedRegardlessOfWishStatus() throws Exception {
        UUID wishId = publishedWish("已完成的願望");

        // 草稿／上架中都能傳
        uploadAs(AttachmentPurpose.WISH_LETTER, wishId, JPEG, ORG_USER);

        UUID claimId = claimAs(wishId, DONOR);
        uploadAs(AttachmentPurpose.SHIPPING_PROOF, claimId, JPEG, DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                ShipRequests.of("郵局", "R123")), DONOR)).andExpect(status().isOk());
        mvc.perform(as(post("/api/organizations/me/claims/{id}/receive", claimId), ORG_USER))
                .andExpect(status().isOk());
        mvc.perform(as(post("/api/organizations/me/claims/{id}/complete", claimId), ORG_USER))
                .andExpect(status().isOk());

        // 已完成之後還是能補傳——感謝卡通常是這個時候才有；限一張，補傳直接
        // 取代第一張，不是疊加
        uploadAs(AttachmentPurpose.WISH_LETTER, wishId, JPEG, ORG_USER);

        mvc.perform(get("/api/wishes/{id}", wishId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.letterPhotoUrls.length()").value(1))
                .andExpect(jsonPath("$.letterPhotoUrls[0]").value(
                        Matchers.not(Matchers.containsString("signature"))));
    }

    @Test
    @DisplayName("換新願望信件照片時舊照片連同檔案一起汰除，跟願望示意圖同一套機制")
    void replacingTheWishLetterPhotoRemovesTheOldFile() throws Exception {
        UUID wishId = publishedWish("換信件照片");

        uploadAs(AttachmentPurpose.WISH_LETTER, wishId, JPEG, ORG_USER);
        assertThat(fakeStorage.size()).isOne();

        uploadAs(AttachmentPurpose.WISH_LETTER, wishId, JPEG, ORG_USER);
        assertThat(fakeStorage.size())
                .as("舊檔案應該被刪掉，不該累積")
                .isOne();

        mvc.perform(get("/api/wishes/{id}", wishId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.letterPhotoUrls.length()").value(1));
    }

    @Test
    @DisplayName("別的機構不能替這個願望上傳信件照片")
    void otherOrganizationsCannotUploadWishLetters() throws Exception {
        UUID wishId = publishedWish("別家的願望");
        var request = new UploadUrlRequest(AttachmentPurpose.WISH_LETTER, wishId, JPEG, 1_000);

        mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), OTHER_ORG_USER))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("民眾不能上傳願望信件照片")
    void donorsCannotUploadWishLetters() throws Exception {
        UUID wishId = publishedWish("民眾不能傳信件照片");
        var request = new UploadUrlRequest(AttachmentPurpose.WISH_LETTER, wishId, JPEG, 1_000);

        mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), DONOR))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_ORG_MEMBER"));
    }

    @Test
    @DisplayName("機構可以刪除自己上傳的信件照片，別的機構不行")
    void organizationCanDeleteOwnWishLetterPhoto() throws Exception {
        UUID wishId = publishedWish("可以刪除信件照片");
        UUID attachmentId = uploadAs(AttachmentPurpose.WISH_LETTER, wishId, JPEG, ORG_USER);

        mvc.perform(as(delete("/api/attachments/{id}", attachmentId), OTHER_ORG_USER))
                .andExpect(status().isNotFound());

        mvc.perform(as(delete("/api/attachments/{id}", attachmentId), ORG_USER))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("願望信件照片不出現在願望牆清單，機構後台的願望清單看得到且帶附件 id")
    void wishLetterAppearsOnOrgListButNotThePublicWall() throws Exception {
        UUID wishId = publishedWish("信件照片清單檢查");
        uploadAs(AttachmentPurpose.WISH_LETTER, wishId, JPEG, ORG_USER);

        mvc.perform(get("/api/wishes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].letterPhotoUrls").doesNotExist());

        mvc.perform(as(get("/api/organizations/me/wishes"), ORG_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].letterPhotos.length()").value(1))
                .andExpect(jsonPath("$.content[0].letterPhotos[0].id").exists());
    }
}
