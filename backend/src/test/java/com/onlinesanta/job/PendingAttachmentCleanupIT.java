package com.onlinesanta.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import com.onlinesanta.admin.AdminAuditAction;
import com.onlinesanta.admin.AdminAuditLogRepository;
import com.onlinesanta.attachment.AttachmentPurpose;
import com.onlinesanta.attachment.dto.UploadUrlRequest;
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

@DisplayName("PENDING 附件的清理排程")
class PendingAttachmentCleanupIT extends ApiIntegrationTest {

    private static final String ADMIN = "platform-admin@example.com";
    private static final String ORG_USER = "cleanup-org@example.org";
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

    @Autowired
    JdbcTemplate jdbc;

    @PersistenceContext
    EntityManager entityManager;

    private InMemoryObjectStorage fakeStorage;
    private Organization organization;

    @BeforeEach
    void setUp() {
        fakeStorage = (InMemoryObjectStorage) storage;
        fakeStorage.reset();

        organization = Organization.register(
                "清理測試機構", "王承辦", "contact@example.org", null, null, null);
        organization.approve(null, "測試資料");
        organizations.save(organization);

        User member = User.newDonor(TestJwtSupport.uidFor(ORG_USER), ORG_USER, ORG_USER);
        member.joinOrganization(organization.getId());
        users.save(member);
    }

    private UUID draftWish(String title) {
        return wishes.save(Wish.draft(organization, "小星", AgeRange.AGE_7_9, "畫畫",
                title, "描述", WishCategory.ART, PriceRange.UNDER_500)).getId();
    }

    private String objectNameOf(String uploadUrl, AttachmentPurpose purpose) {
        int start = uploadUrl.indexOf(purpose.prefix());
        return uploadUrl.substring(start, uploadUrl.indexOf('?'));
    }

    /** 只索取上傳網址、視需要模擬直傳，但不呼叫 confirm——留在 PENDING。 */
    private record PendingUpload(UUID attachmentId, String objectName) {
    }

    private PendingUpload requestUpload(boolean actuallyUploaded) throws Exception {
        UUID wishId = draftWish("待清理的願望");
        var request = new UploadUrlRequest(AttachmentPurpose.WISH_IMAGE, wishId, JPEG, 1_000);
        String body = mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), ORG_USER))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var node = json.readTree(body);
        UUID attachmentId = UUID.fromString(node.get("attachmentId").asText());
        String objectName = objectNameOf(node.get("uploadUrl").asText(), AttachmentPurpose.WISH_IMAGE);

        if (actuallyUploaded) {
            fakeStorage.simulateUpload(StorageBucket.PUBLIC, objectName, JPEG, 1_000);
        }
        return new PendingUpload(attachmentId, objectName);
    }

    private UUID confirmedAttachment() throws Exception {
        PendingUpload uploaded = requestUpload(true);
        mvc.perform(as(post("/api/attachments/{id}/confirm", uploaded.attachmentId()), ORG_USER))
                .andExpect(status().isOk());
        return uploaded.attachmentId();
    }

    private void backdateCreatedAt(UUID attachmentId, Instant createdAt) {
        entityManager.flush();
        jdbc.update("UPDATE attachments SET created_at = ? WHERE id = ?",
                Timestamp.from(createdAt), attachmentId);
        entityManager.clear();
    }

    /**
     * 查詢前先 flush：清理排程的刪除是透過 JPA repository 做的，在同一個測試交易裡
     * （{@code ApiIntegrationTest} 整個方法包在一個交易中回滾）不會自動同步到
     * 繞過 Hibernate 的原生 JDBC 查詢，得手動 flush 才看得到剛刪除的結果。
     */
    private boolean attachmentExists(UUID attachmentId) {
        entityManager.flush();
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM attachments WHERE id = ?", Integer.class, attachmentId);
        return count != null && count > 0;
    }

    private String cleanup() throws Exception {
        return mvc.perform(as(post("/api/admin/jobs/cleanup-pending-attachments"), ADMIN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    // ------------------------------------------------------------ 核心行為

    @Test
    @DisplayName("超過 24 小時的 PENDING 附件會被清掉，含儲存端物件")
    void expiredPendingAttachmentsAreCleaned() throws Exception {
        // 這筆真的傳上去了（PUT 成功），但沒有回頭 confirm——孤兒物件
        PendingUpload uploaded = requestUpload(true);
        backdateCreatedAt(uploaded.attachmentId(), Instant.now().minus(25, ChronoUnit.HOURS));
        assertThat(fakeStorage.exists(StorageBucket.PUBLIC, uploaded.objectName())).isTrue();

        mvc.perform(as(post("/api/admin/jobs/cleanup-pending-attachments"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(1))
                .andExpect(jsonPath("$.deleted").value(1))
                .andExpect(jsonPath("$.failed").value(0));

        assertThat(attachmentExists(uploaded.attachmentId())).isFalse();
        assertThat(fakeStorage.exists(StorageBucket.PUBLIC, uploaded.objectName())).isFalse();
    }

    @Test
    @DisplayName("即使物件根本沒真的傳上去（只拿了網址就放棄），清理也視為成功")
    void orphanRecordsWithNoActualUploadAreAlsoCleaned() throws Exception {
        PendingUpload uploaded = requestUpload(false);
        backdateCreatedAt(uploaded.attachmentId(), Instant.now().minus(48, ChronoUnit.HOURS));

        mvc.perform(as(post("/api/admin/jobs/cleanup-pending-attachments"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(1))
                .andExpect(jsonPath("$.deleted").value(1));

        assertThat(attachmentExists(uploaded.attachmentId())).isFalse();
    }

    @Test
    @DisplayName("未滿 24 小時的 PENDING 附件不受影響")
    void freshPendingAttachmentsAreUntouched() throws Exception {
        PendingUpload uploaded = requestUpload(true);
        // 不 backdate：createdAt 就是現在

        mvc.perform(as(post("/api/admin/jobs/cleanup-pending-attachments"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(0))
                .andExpect(jsonPath("$.deleted").value(0));

        assertThat(attachmentExists(uploaded.attachmentId())).isTrue();
        assertThat(fakeStorage.exists(StorageBucket.PUBLIC, uploaded.objectName())).isTrue();
    }

    @Test
    @DisplayName("已確認的附件不受影響，即使已經是很久以前建立的")
    void confirmedAttachmentsAreNeverTouchedRegardlessOfAge() throws Exception {
        UUID attachmentId = confirmedAttachment();
        backdateCreatedAt(attachmentId, Instant.now().minus(365, ChronoUnit.DAYS));

        mvc.perform(as(post("/api/admin/jobs/cleanup-pending-attachments"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(0))
                .andExpect(jsonPath("$.deleted").value(0));

        assertThat(attachmentExists(attachmentId)).isTrue();
    }

    @Test
    @DisplayName("一次能處理多筆，且只挑中真的過期的那些")
    void cleanupHandlesMultipleAttachmentsAndOnlyPicksExpiredOnes() throws Exception {
        PendingUpload expiredA = requestUpload(true);
        backdateCreatedAt(expiredA.attachmentId(), Instant.now().minus(30, ChronoUnit.HOURS));

        PendingUpload expiredB = requestUpload(true);
        backdateCreatedAt(expiredB.attachmentId(), Instant.now().minus(72, ChronoUnit.HOURS));

        PendingUpload fresh = requestUpload(true);
        UUID confirmed = confirmedAttachment();

        mvc.perform(as(post("/api/admin/jobs/cleanup-pending-attachments"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(2))
                .andExpect(jsonPath("$.deleted").value(2));

        assertThat(attachmentExists(expiredA.attachmentId())).isFalse();
        assertThat(attachmentExists(expiredB.attachmentId())).isFalse();
        assertThat(attachmentExists(fresh.attachmentId())).isTrue();
        assertThat(attachmentExists(confirmed)).isTrue();
    }

    @Test
    @DisplayName("重複執行不會出錯，第二次找不到東西可清")
    void runningTwiceIsSafe() throws Exception {
        PendingUpload uploaded = requestUpload(true);
        backdateCreatedAt(uploaded.attachmentId(), Instant.now().minus(25, ChronoUnit.HOURS));

        cleanup();
        mvc.perform(as(post("/api/admin/jobs/cleanup-pending-attachments"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(0));
    }

    // ------------------------------------------------------------ 稽核與授權

    @Test
    @DisplayName("手動觸發會寫入稽核紀錄")
    void manualTriggerIsAudited() throws Exception {
        PendingUpload uploaded = requestUpload(true);
        backdateCreatedAt(uploaded.attachmentId(), Instant.now().minus(25, ChronoUnit.HOURS));

        cleanup();

        assertThat(auditLogs.findAll())
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.getAction()).isEqualTo(AdminAuditAction.RUN_ATTACHMENT_CLEANUP);
                    assertThat(log.getTargetId()).isNull();
                    assertThat(log.getDetail()).contains("清除 1 筆");
                });
    }

    @Test
    @DisplayName("手動觸發限管理員")
    void manualTriggerIsAdminOnly() throws Exception {
        mvc.perform(as(post("/api/admin/jobs/cleanup-pending-attachments"), ORG_USER))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/jobs/cleanup-pending-attachments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("排程端點需要 Google OIDC token，Firebase token 無效")
    void internalEndpointRejectsRegularUserTokens() throws Exception {
        mvc.perform(as(post("/internal/jobs/cleanup-pending-attachments"), ADMIN))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/jobs/cleanup-pending-attachments"))
                .andExpect(status().isUnauthorized());
    }
}
