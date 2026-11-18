package com.onlinesanta.closing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.onlinesanta.admin.AdminAuditAction;
import com.onlinesanta.admin.AdminAuditLogRepository;
import com.onlinesanta.attachment.AttachmentPurpose;
import com.onlinesanta.closing.dto.CampaignRequest;
import com.onlinesanta.closing.dto.VoidReceiptRequest;
import com.onlinesanta.common.TaiwanYear;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.ShipRequests;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishCategory;
import com.onlinesanta.wish.WishRepository;

@DisplayName("後台收據 API：清單、下載、作廢重開、補開、補產")
class AdminReceiptApiIT extends ApiIntegrationTest {

    private static final String ADMIN = "platform-admin@example.com";
    private static final String ORG_USER = "receipt-api-org@example.org";
    private static final String DONOR = "receipt-api-donor@example.com";
    private static final LocalDate TODAY = LocalDate.now(TaiwanYear.ZONE);

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    UserRepository users;

    @Autowired
    WishRepository wishes;

    @Autowired
    CampaignService campaignService;

    @Autowired
    CampaignRepository campaigns;

    @Autowired
    DonationReceiptRepository receipts;

    @Autowired
    AdminAuditLogRepository auditLogs;

    private Organization organization;

    @BeforeEach
    void setUp() {
        organization = Organization.register("收據API機構", "王承辦", "contact@example.org", null, null, null);
        organization.approve(null, "測試資料");
        organizations.save(organization);
        User member = User.newDonor(ORG_USER, ORG_USER);
        member.joinOrganization(organization.getId());
        users.save(member);
        users.save(User.newDonor(DONOR, "熱心民眾"));
    }

    private Campaign campaign(String permit) {
        return campaignService.create(new CampaignRequest("API 檔期", "115000877", permit,
                "線上聖誕老人2026聖誕禮物勸募活動", TODAY.minusDays(5), TODAY.plusDays(60),
                TODAY.minusDays(5), TODAY.plusDays(60), null, "OSA", null, null, null));
    }

    private UUID completedClaim(String title) throws Exception {
        Wish wish = Wish.draft(organization, "小星", AgeRange.AGE_7_9, "畫畫", title, "描述", WishCategory.ART,
                PriceRange.UNDER_500);
        wish.publish();
        UUID wishId = wishes.save(wish).getId();
        String body = mvc.perform(as(post("/api/wishes/{id}/claim", wishId), DONOR))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID claimId = UUID.fromString(json.readTree(body).get("id").asText());
        uploadConfirmedAttachment(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId), ShipRequests.of("郵局", "R1")), DONOR))
                .andExpect(status().isOk());
        uploadConfirmedAttachment(AttachmentPurpose.ORG_FEEDBACK, claimId, ORG_USER);
        mvc.perform(as(withBody(post("/api/organizations/me/claims/{id}/complete", claimId),
                        Map.of("inspected", true)), ORG_USER)).andExpect(status().isOk());
        return claimId;
    }

    @Test
    @DisplayName("清單依檔期列出，可依狀態與關鍵字篩選，且不含證號")
    void listsAndFiltersReceipts() throws Exception {
        Campaign c = campaign("衛部救字第1150000號");
        completedClaim("第一個");
        completedClaim("第二個");

        mvc.perform(as(get("/api/admin/receipts").param("campaignId", c.getId().toString()), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].receiptNo").value("OSA-000001"))
                .andExpect(jsonPath("$.content[0].donorName").value("善心人士"))
                .andExpect(jsonPath("$.content[0].giftValue").value(500))
                .andExpect(jsonPath("$.content[0].donorIdEncrypted").doesNotExist())
                .andExpect(jsonPath("$.content[0].idNumber").doesNotExist());
        mvc.perform(as(get("/api/admin/receipts").param("campaignId", c.getId().toString())
                        .param("q", "000002"), ADMIN))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(as(get("/api/admin/receipts").param("campaignId", c.getId().toString())
                        .param("status", "VOIDED"), ADMIN))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("只有管理員能用")
    void adminOnly() throws Exception {
        Campaign c = campaign("衛部救字第1150000號");
        mvc.perform(as(get("/api/admin/receipts").param("campaignId", c.getId().toString()), DONOR))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("下載網址：產生 PDF、回傳短效網址，並寫稽核（不含證號）")
    void downloadUrlGeneratesPdfAndAudits() throws Exception {
        campaign("衛部救字第1150000號");
        UUID claimId = completedClaim("要下載");
        UUID receiptId = receipts.findValidByClaimId(claimId).orElseThrow().getId();

        mvc.perform(as(get("/api/admin/receipts/{id}/download-url", receiptId).param("kind", "WHITE"), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.containsString("closing/receipts/OSA-000001/white.pdf")))
                .andExpect(jsonPath("$.expiresAt").exists());

        assertThat(receipts.findById(receiptId).orElseThrow().getPdfStatus()).isEqualTo(PdfStatus.READY);
        assertThat(auditLogs.findAll()).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo(AdminAuditAction.VIEW_RECEIPT_DOCUMENT);
            assertThat(log.getDetail()).contains("OSA-000001");
        });
    }

    @Test
    @DisplayName("檔期缺許可文號：下載回 409 並說明；補上文號後一鍵補產")
    void pendingPermitBlocksDownloadUntilGenerated() throws Exception {
        Campaign c = campaign(null);
        UUID claimId = completedClaim("缺文號");
        UUID receiptId = receipts.findValidByClaimId(claimId).orElseThrow().getId();

        mvc.perform(as(get("/api/admin/receipts/{id}/download-url", receiptId).param("kind", "WHITE"), ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RECEIPT_PDF_NOT_READY"));

        Campaign loaded = campaigns.findById(c.getId()).orElseThrow();
        loaded.update(loaded.getName(), null, "衛部救字第1150000號", null, loaded.getCollectionStart(),
                loaded.getCollectionEnd(), loaded.getUseStart(), loaded.getUseEnd(), null, loaded.getReceiptPrefix(),
                null, null, CampaignStatus.ACTIVE);
        campaigns.saveAndFlush(loaded);

        mvc.perform(as(post("/api/admin/campaigns/{id}/generate-receipt-pdfs", c.getId()), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done").value(1))
                .andExpect(jsonPath("$.pending").value(0));
    }

    @Test
    @DisplayName("作廢重開：API 回新收據，舊的變作廢並寫稽核；原因必填")
    void voidViaApi() throws Exception {
        Campaign c = campaign("衛部救字第1150000號");
        UUID claimId = completedClaim("要作廢");
        UUID receiptId = receipts.findValidByClaimId(claimId).orElseThrow().getId();

        mvc.perform(as(withBody(post("/api/admin/receipts/{id}/void", receiptId), new VoidReceiptRequest("")), ADMIN))
                .andExpect(status().isBadRequest());
        mvc.perform(as(withBody(post("/api/admin/receipts/{id}/void", receiptId),
                        new VoidReceiptRequest("捐贈者資料填錯")), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptNo").value("OSA-000002"))
                .andExpect(jsonPath("$.status").value("ISSUED"));

        mvc.perform(as(get("/api/admin/receipts").param("campaignId", c.getId().toString())
                        .param("status", "VOIDED"), ADMIN))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].voidReason").value("捐贈者資料填錯"));
        assertThat(auditLogs.findAll()).anySatisfy(log ->
                assertThat(log.getAction()).isEqualTo(AdminAuditAction.VOID_RECEIPT));
    }

    @Test
    @DisplayName("補開：檔期晚於願望完成才建立時，為已完成的認領依完成順序補開，重複補開不會多開")
    void issueMissingBackfillsInCompletionOrder() throws Exception {
        UUID first = completedClaim("先完成");      // 當時還沒有檔期，不開收據
        UUID second = completedClaim("後完成");
        assertThat(receipts.findValidByClaimId(first)).isEmpty();
        Campaign c = campaign("衛部救字第1150000號");

        mvc.perform(as(post("/api/admin/campaigns/{id}/issue-missing-receipts", c.getId()), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done").value(2));

        assertThat(receipts.findValidByClaimId(first).orElseThrow().getReceiptNo()).isEqualTo("OSA-000001");
        assertThat(receipts.findValidByClaimId(second).orElseThrow().getReceiptNo()).isEqualTo("OSA-000002");
        mvc.perform(as(post("/api/admin/campaigns/{id}/issue-missing-receipts", c.getId()), ADMIN))
                .andExpect(jsonPath("$.done").value(0));
    }
}
