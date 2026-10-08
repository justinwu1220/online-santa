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
import com.onlinesanta.attachment.AttachmentPurpose;
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
        var request = new UploadUrlRequest(purpose, targetId, "image/jpeg", 120_000);
        String body = mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), userEmail))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var node = json.readTree(body);
        UUID attachmentId = UUID.fromString(node.get("attachmentId").asText());
        String uploadUrl = node.get("uploadUrl").asText();
        String objectName = uploadUrl.substring(
                uploadUrl.indexOf(purpose.prefix()), uploadUrl.indexOf('?'));

        ((InMemoryObjectStorage) storage)
                .simulateUpload(purpose.bucket(), objectName, "image/jpeg", 120_000);

        mvc.perform(as(post("/api/attachments/{id}/confirm", attachmentId), userEmail))
                .andExpect(status().isOk());
        return attachmentId;
    }
}
