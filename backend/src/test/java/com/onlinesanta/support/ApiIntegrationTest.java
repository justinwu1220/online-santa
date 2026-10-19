package com.onlinesanta.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinesanta.attachment.Attachment;
import com.onlinesanta.attachment.AttachmentPurpose;
import com.onlinesanta.attachment.AttachmentRepository;
import com.onlinesanta.attachment.dto.UploadUrlRequest;
import com.onlinesanta.storage.ObjectStorage;

/**
 * HTTP 層整合測試的基底。
 *
 * <p>{@code @Transactional} 讓每個測試結束後自動回滾，測試之間不會互相汙染。
 */
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@Transactional
public abstract class ApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected ObjectStorage storage;

    @Autowired
    protected AttachmentRepository attachmentRepository;

    /**
     * 直接寫入一份已確認的機構證明文件。給沒有機構成員、無法走上傳流程的測試機構用
     * （核准前要求至少有一份文件）。
     */
    protected void givenConfirmedOrganizationDocument(UUID organizationId) {
        Attachment document = Attachment.pending(AttachmentPurpose.ORG_DOCUMENT, organizationId,
                "org-documents/" + UUID.randomUUID() + ".jpg", "image/jpeg", null, null);
        document.confirm("image/jpeg", 120_000);
        attachmentRepository.save(document);
    }

    /**
     * 機構「確認收到並完成」：先上傳一張收件證明（必填），再送出（收到日期用今天、已拆封檢查）。
     * 回傳結果讓呼叫端接著斷言。認領必須是已寄出。
     */
    protected org.springframework.test.web.servlet.ResultActions completeAsOrganization(
            UUID claimId, String organizationUser) throws Exception {
        uploadConfirmedAttachment(AttachmentPurpose.ORG_FEEDBACK, claimId, organizationUser);
        return mvc.perform(as(withBody(
                post("/api/organizations/me/claims/{id}/complete", claimId),
                java.util.Map.of("inspected", true)), organizationUser));
    }

    /** 測試用的平台管理員（application-test.yml 的 admin-emails）。 */
    protected static final String PLATFORM_ADMIN = "platform-admin@example.com";

    /**
     * 讓草稿願望走完審核上架：機構上傳信件照片、提交審核，再由管理員核准。
     * 機構必須已通過審核。
     */
    protected void submitAndApproveWish(UUID wishId, String organizationUser) throws Exception {
        uploadConfirmedAttachment(AttachmentPurpose.WISH_LETTER, wishId, organizationUser);
        mvc.perform(as(post("/api/wishes/{id}/submit", wishId), organizationUser))
                .andExpect(status().isOk());
        mvc.perform(as(post("/api/admin/wishes/{id}/approve", wishId), PLATFORM_ADMIN))
                .andExpect(status().isOk());
    }

    /** 核准機構的另一個前提：已確認的機構照片。 */
    protected void givenConfirmedOrganizationPhoto(UUID organizationId) {
        Attachment photo = Attachment.pending(AttachmentPurpose.ORG_PHOTO, organizationId,
                "org-photos/" + UUID.randomUUID() + ".jpg", "image/jpeg", null, null);
        photo.confirm("image/jpeg", 120_000);
        attachmentRepository.save(photo);
    }

    /**
     * 以指定的 email 作為操作者發送請求。
     *
     * <p>帶的是真正簽章過的 ID token，會完整走過解碼、驗證與身分轉換的流程。
     */
    protected MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, String email) {
        return builder.header("Authorization", "Bearer " + TestJwtSupport.tokenFor(email));
    }

    /** 以「信箱尚未驗證」的身分發送請求——對應密碼註冊但還沒點驗證信的使用者。 */
    protected MockHttpServletRequestBuilder asUnverified(
            MockHttpServletRequestBuilder builder, String email) {
        return builder.header("Authorization",
                "Bearer " + TestJwtSupport.unverifiedTokenFor(email));
    }

    protected MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder builder,
                                                     Object body) {
        try {
            return builder.contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body));
        } catch (Exception e) {
            throw new IllegalStateException("序列化測試請求內容失敗", e);
        }
    }

    /**
     * 走完三步驟（索取網址 → 模擬前端直傳 → 確認）造出一筆已確認的附件。
     *
     * <p>給那些「附件本身不是測試重點、只是某個業務規則的前提」的測試用——例如
     * {@code ClaimService.ship()} 現在要求至少有一張已確認的寄送證明。真正測附件
     * 上傳流程本身的測試（{@code AttachmentApiIT} 等）請繼續用它們自己那套更仔細的
     * 斷言，這裡只負責「讓前提成立」。
     */
    protected UUID uploadConfirmedAttachment(AttachmentPurpose purpose, UUID targetId, String userEmail)
            throws Exception {
        return uploadConfirmedAttachment(purpose, targetId, userEmail, "image/jpeg");
    }

    /** 同上，可指定內容類型（例如測試 PDF）。 */
    protected UUID uploadConfirmedAttachment(AttachmentPurpose purpose, UUID targetId, String userEmail,
                                             String contentType) throws Exception {
        var request = new UploadUrlRequest(purpose, targetId, contentType, 120_000);
        String body = mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), userEmail))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var node = json.readTree(body);
        UUID attachmentId = UUID.fromString(node.get("attachmentId").asText());
        String uploadUrl = node.get("uploadUrl").asText();
        String objectName = uploadUrl.substring(
                uploadUrl.indexOf(purpose.prefix()), uploadUrl.indexOf('?'));

        ((InMemoryObjectStorage) storage)
                .simulateUpload(purpose.bucket(), objectName, contentType, 120_000);

        mvc.perform(as(post("/api/attachments/{id}/confirm", attachmentId), userEmail))
                .andExpect(status().isOk());
        return attachmentId;
    }
}
