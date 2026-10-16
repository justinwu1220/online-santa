package com.onlinesanta.claim;

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
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.onlinesanta.admin.AdminAuditAction;
import com.onlinesanta.admin.AdminAuditLogRepository;
import com.onlinesanta.attachment.AttachmentPurpose;
import com.onlinesanta.claim.dto.ReceiptRequest;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.ShipRequests;
import com.onlinesanta.support.TestJwtSupport;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishCategory;
import com.onlinesanta.wish.WishRepository;

@DisplayName("捐贈收據")
class ClaimReceiptApiIT extends ApiIntegrationTest {

    private static final String ORG_USER = "org@example.org";
    private static final String DONOR = "donor@example.com";
    private static final String OTHER_DONOR = "other-donor@example.com";
    private static final String ADMIN = "platform-admin@example.com";

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    UserRepository users;

    @Autowired
    WishRepository wishes;

    @Autowired
    AdminAuditLogRepository auditLogs;

    @Autowired
    JdbcTemplate jdbc;

    private Organization organization;

    @BeforeEach
    void setUp() {
        Organization org = Organization.register("送禮之家", "王承辦", "contact@example.org",
                "02-1234-5678", "0912-345-678", "台北市中正區某某路 1 號", null, null);
        org.approve(null, "測試資料");
        organization = organizations.save(org);

        User member = User.newDonor(TestJwtSupport.uidFor(ORG_USER), ORG_USER, ORG_USER);
        member.joinOrganization(organization.getId());
        users.save(member);

        users.save(User.newDonor(TestJwtSupport.uidFor(DONOR), DONOR, "熱心民眾"));
        users.save(User.newDonor(TestJwtSupport.uidFor(OTHER_DONOR), OTHER_DONOR, "另一位民眾"));
    }

    private UUID claimedWish(String title) throws Exception {
        Wish wish = Wish.draft(organization, "小星", AgeRange.AGE_7_9, "畫畫",
                title, "描述", WishCategory.ART, PriceRange.UNDER_500);
        wish.publish();
        UUID wishId = wishes.save(wish).getId();

        String body = mvc.perform(as(post("/api/wishes/{id}/claim", wishId), DONOR))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    /** 已經傳好寄送證明與發票證明，只差呼叫 /ship。 */
    private UUID claimReadyToShip(String title) throws Exception {
        UUID claimId = claimedWish(title);
        uploadConfirmedAttachment(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);
        uploadConfirmedAttachment(AttachmentPurpose.RECEIPT_PROOF, claimId, DONOR, "application/pdf");
        return claimId;
    }

    private void shipWithReceipt(UUID claimId) throws Exception {
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                        ShipRequests.withReceipt("郵局", "R1")), DONOR))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------ 送出

    @Test
    @DisplayName("申請收據但沒有發票證明不能回報寄送")
    void receiptRequiresAProofFile() throws Exception {
        UUID claimId = claimedWish("沒傳發票");
        uploadConfirmedAttachment(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);

        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                        ShipRequests.withReceipt("郵局", "R1")), DONOR))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RECEIPT_PROOF_REQUIRED"));

        // 不申請收據的回報不受影響
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                        ShipRequests.of("郵局", "R1")), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptRequested").value(false));
    }

    @Test
    @DisplayName("收據欄位缺漏或格式不對會被拒絕，錯誤指到對應欄位")
    void validatesReceiptFields() throws Exception {
        UUID claimId = claimReadyToShip("驗證收據欄位");

        var invalid = new ReceiptRequest("", "123", "not-an-email", "");
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                        ShipRequests.withReceipt("郵局", "R1", invalid)), DONOR))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors['receipt.name']").exists())
                .andExpect(jsonPath("$.fieldErrors['receipt.idNumber']").exists())
                .andExpect(jsonPath("$.fieldErrors['receipt.email']").exists())
                .andExpect(jsonPath("$.fieldErrors['receipt.address']").exists());
    }

    @Test
    @DisplayName("身分證字號與統一編號都能通過，小寫英文字母會轉成大寫存放")
    void acceptsNationalIdAndCompanyNumber() throws Exception {
        UUID personal = claimReadyToShip("個人收據");
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", personal),
                        ShipRequests.withReceipt("郵局", "R1",
                                new ReceiptRequest("王大明", "a123456789", "a@example.com", "台北市"))),
                        DONOR))
                .andExpect(status().isOk());

        UUID company = claimReadyToShip("公司收據");
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", company),
                        ShipRequests.withReceipt("郵局", "R2",
                                new ReceiptRequest("某某股份有限公司", "12345678", "b@example.com", "台北市"))),
                        DONOR))
                .andExpect(status().isOk());

        mvc.perform(as(get("/api/admin/claims/{id}/receipt", personal), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idNumber").value("A123456789"));
    }

    @Test
    @DisplayName("送出後捐贈者看得到「已申請」旗標")
    void donorSeesTheRequestedFlag() throws Exception {
        UUID claimId = claimReadyToShip("旗標");
        shipWithReceipt(claimId);

        mvc.perform(as(get("/api/claims/{id}", claimId), DONOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptRequested").value(true))
                // 旗標之外不回任何收據內容
                .andExpect(jsonPath("$.idNumber").doesNotExist())
                .andExpect(jsonPath("$.receipt").doesNotExist());
    }

    // ------------------------------------------------------------ 機構看不到

    @Test
    @DisplayName("機構完全看不到收據資料：認領詳情、清單與附件都沒有")
    void organizationCannotSeeAnyReceiptData() throws Exception {
        UUID claimId = claimReadyToShip("機構看不到");
        shipWithReceipt(claimId);

        String detail = mvc.perform(as(get("/api/organizations/me/claims/{id}", claimId), ORG_USER))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(detail)
                .doesNotContain("receipt")
                .doesNotContain("A123456789")
                .doesNotContain("receipt@example.com");

        String list = mvc.perform(as(get("/api/organizations/me/claims"), ORG_USER))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain("receipt").doesNotContain("A123456789");

        // 附件清單只有寄送證明，沒有發票證明
        String attachments = mvc.perform(as(get("/api/claims/{id}/attachments", claimId), ORG_USER))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode nodes = json.readTree(attachments);
        assertThat(nodes).isNotEmpty();
        nodes.forEach(node -> assertThat(node.get("purpose").asText()).isNotEqualTo("RECEIPT_PROOF"));

        // 管理端點對機構一律擋下
        mvc.perform(as(get("/api/admin/claims/{id}/receipt", claimId), ORG_USER))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("機構不能替認領上傳發票證明，其他捐贈者也不行")
    void onlyTheDonorCanUploadReceiptProof() throws Exception {
        UUID claimId = claimedWish("只有本人能傳");

        var request = new com.onlinesanta.attachment.dto.UploadUrlRequest(
                AttachmentPurpose.RECEIPT_PROOF, claimId, "image/jpeg", 120_000);
        mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), ORG_USER))
                .andExpect(status().is4xxClientError());
        mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), OTHER_DONOR))
                .andExpect(status().isNotFound());
        mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), DONOR))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------ 加密與管理端

    @Test
    @DisplayName("身分證字號在資料庫是密文，管理端點讀回明文")
    void idNumberIsEncryptedAtRest() throws Exception {
        UUID claimId = claimReadyToShip("加密");
        shipWithReceipt(claimId);

        String stored = jdbc.queryForObject(
                "select id_number_encrypted from claim_receipts where claim_id = ?",
                String.class, claimId);
        assertThat(stored).isNotBlank().doesNotContain("A123456789");

        mvc.perform(as(get("/api/admin/claims/{id}/receipt", claimId), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("王大明"))
                .andExpect(jsonPath("$.idNumber").value("A123456789"))
                .andExpect(jsonPath("$.email").value("receipt@example.com"))
                .andExpect(jsonPath("$.address").value("台北市中正區某路 1 號"))
                .andExpect(jsonPath("$.proofs.length()").value(1))
                .andExpect(jsonPath("$.proofs[0].purpose").value("RECEIPT_PROOF"));
    }

    @Test
    @DisplayName("管理員查閱收據資料會寫稽核，稽核內容不含證號")
    void viewingReceiptIsAudited() throws Exception {
        UUID claimId = claimReadyToShip("稽核");
        shipWithReceipt(claimId);
        long before = auditLogs.count();

        mvc.perform(as(get("/api/admin/claims/{id}/receipt", claimId), ADMIN))
                .andExpect(status().isOk());

        assertThat(auditLogs.count()).isEqualTo(before + 1);
        var log = auditLogs.findAll().stream()
                .filter(entry -> entry.getAction() == AdminAuditAction.VIEW_CLAIM_RECEIPT)
                .findFirst().orElseThrow();
        assertThat(log.getTargetId()).isEqualTo(claimId);
        assertThat(log.getDetail()).contains("送禮之家").doesNotContain("A123456789");
    }

    @Test
    @DisplayName("稽核紀錄頁的 API 查得到收據查閱，並能依動作篩選")
    void receiptViewsAppearInTheAuditTrailEndpoint() throws Exception {
        UUID claimId = claimReadyToShip("稽核列表");
        shipWithReceipt(claimId);

        mvc.perform(as(get("/api/admin/claims/{id}", claimId), ADMIN)).andExpect(status().isOk());
        mvc.perform(as(get("/api/admin/claims/{id}/receipt", claimId), ADMIN))
                .andExpect(status().isOk());

        // 只篩收據查閱：詳情那一筆不會混進來
        mvc.perform(as(get("/api/admin/audit-logs").param("action", "VIEW_CLAIM_RECEIPT"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value("VIEW_CLAIM_RECEIPT"))
                .andExpect(jsonPath("$.content[0].targetType").value("CLAIM"))
                .andExpect(jsonPath("$.content[0].targetId").value(claimId.toString()))
                .andExpect(jsonPath("$.content[0].adminEmail").value(ADMIN))
                .andExpect(jsonPath("$.content[0].detail").value(
                        org.hamcrest.Matchers.containsString("送禮之家")))
                .andExpect(jsonPath("$.content[0].detail").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("A123456789"))));
    }

    @Test
    @DisplayName("捐贈者不能讀收據資料；沒有申請的認領回 404 且不寫稽核")
    void receiptEndpointGuards() throws Exception {
        UUID withReceipt = claimReadyToShip("有申請");
        shipWithReceipt(withReceipt);
        mvc.perform(as(get("/api/admin/claims/{id}/receipt", withReceipt), DONOR))
                .andExpect(status().isForbidden());

        UUID without = claimedWish("沒申請");
        long before = auditLogs.count();
        mvc.perform(as(get("/api/admin/claims/{id}/receipt", without), ADMIN))
                .andExpect(status().isNotFound());
        assertThat(auditLogs.count()).isEqualTo(before);
    }

    // ------------------------------------------------------------ 發票證明檔案

    @Test
    @DisplayName("發票證明可以是 PDF，寄送證明仍然不收 PDF")
    void receiptProofAcceptsPdfButShippingProofDoesNot() throws Exception {
        UUID claimId = claimedWish("PDF");

        var pdfProof = new com.onlinesanta.attachment.dto.UploadUrlRequest(
                AttachmentPurpose.RECEIPT_PROOF, claimId, "application/pdf", 120_000);
        mvc.perform(as(withBody(post("/api/uploads/signed-url"), pdfProof), DONOR))
                .andExpect(status().isOk());

        var pdfShipping = new com.onlinesanta.attachment.dto.UploadUrlRequest(
                AttachmentPurpose.SHIPPING_PROOF, claimId, "application/pdf", 120_000);
        mvc.perform(as(withBody(post("/api/uploads/signed-url"), pdfShipping), DONOR))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_CONTENT_TYPE"));
    }
}
