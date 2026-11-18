package com.onlinesanta.closing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.onlinesanta.admin.AdminAuditAction;
import com.onlinesanta.admin.AdminAuditLogRepository;
import com.onlinesanta.attachment.AttachmentPurpose;
import com.onlinesanta.closing.dto.CampaignRequest;
import com.onlinesanta.common.TaiwanYear;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.storage.ObjectStorage;
import com.onlinesanta.storage.StorageBucket;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.ShipRequests;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishCategory;
import com.onlinesanta.wish.WishRepository;

import jakarta.persistence.EntityManager;

@DisplayName("收據匯出（全部下載）與預覽／下載模式")
class ClosingExportIT extends ApiIntegrationTest {

    private static final String ADMIN = "platform-admin@example.com";
    private static final String ORG_USER = "export-org@example.org";
    private static final String DONOR = "export-donor@example.com";
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
    DonationReceiptRepository receipts;

    @Autowired
    ClosingExportService exportService;

    @Autowired
    ClosingExportRepository exportRepository;

    @Autowired
    ObjectStorage storage;

    @Autowired
    AdminAuditLogRepository auditLogs;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entityManager;

    private Organization organization;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        organization = Organization.register("匯出測試機構", "王承辦", "contact@example.org", null, null, null);
        organization.approve(null, "測試資料");
        organizations.save(organization);
        User member = User.newDonor(ORG_USER, ORG_USER);
        member.joinOrganization(organization.getId());
        users.save(member);
        users.save(User.newDonor(DONOR, "熱心民眾"));
        campaign = campaignService.create(new CampaignRequest("匯出檔期", "115000877", "衛部救字第1150000號",
                "線上聖誕老人2026聖誕禮物勸募活動", TODAY.minusDays(5), TODAY.plusDays(60),
                TODAY.minusDays(5), TODAY.plusDays(60), null, "OSE", null, null, null));
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

    private UUID startExport() throws Exception {
        String body = mvc.perform(as(post("/api/admin/campaigns/{id}/receipt-exports", campaign.getId()), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREPARING"))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    private static Map<String, byte[]> unzip(byte[] zip) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                entries.put(e.getName(), in.readAllBytes());
            }
        }
        return entries;
    }

    private static int pages(byte[] pdf) throws Exception {
        try (PDDocument d = Loader.loadPDF(pdf)) {
            return d.getNumberOfPages();
        }
    }

    // ------------------------------------------------------------ 匯出

    @Test
    @DisplayName("分段推進：預算用完先回進度；之後完成並產生壓縮檔（白聯、黃紅聯、清單），到期時間為 30 天後")
    void advancesInStepsAndBuildsTheZip() throws Exception {
        completedClaim("第一個");
        completedClaim("第二個");
        completedClaim("第三個");
        UUID id = startExport();

        // 預算 0：只存進度，不做任何一張
        ClosingExport step = exportService.advance(id, Duration.ZERO);
        assertThat(step.getStatus()).isEqualTo(ExportStatus.PREPARING);
        assertThat(step.getPrepared()).isZero();
        assertThat(step.getTotal()).isEqualTo(3);

        ClosingExport done = exportService.advance(id);
        assertThat(done.getStatus()).isEqualTo(ExportStatus.READY);
        assertThat(done.getSkipped()).isZero();
        assertThat(done.getSizeBytes()).isPositive();
        assertThat(done.getExpiresAt()).isAfter(java.time.Instant.now().plus(Duration.ofDays(29)));

        Map<String, byte[]> zip = unzip(storage.read(StorageBucket.PRIVATE, done.getObjectName()).orElseThrow());
        assertThat(zip.keySet()).containsExactly("00_收據清單.csv",
                "白聯/OSE-000001.pdf", "黃紅聯/OSE-000001.pdf",
                "白聯/OSE-000002.pdf", "黃紅聯/OSE-000002.pdf",
                "白聯/OSE-000003.pdf", "黃紅聯/OSE-000003.pdf");
        assertThat(pages(zip.get("白聯/OSE-000002.pdf"))).isEqualTo(1);
        assertThat(pages(zip.get("黃紅聯/OSE-000002.pdf"))).isEqualTo(2);
        String csv = new String(zip.get("00_收據清單.csv"), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿編號,").contains("OSE-000001", "OSE-000003").contains("匯出測試機構");
        assertThat(csv.lines().count()).isEqualTo(4);      // 欄名＋三張
    }

    @Test
    @DisplayName("已完成的作業再呼叫 advance 無害，內容不變")
    void advancingAFinishedExportIsHarmless() throws Exception {
        completedClaim("一張");
        UUID id = startExport();
        ClosingExport done = exportService.advance(id);
        String object = done.getObjectName();

        ClosingExport again = exportService.advance(id);

        assertThat(again.getStatus()).isEqualTo(ExportStatus.READY);
        assertThat(again.getObjectName()).isEqualTo(object);
    }

    @Test
    @DisplayName("PDF 無法產生的收據不收進壓縮檔，並在說明檔列出原因，作業仍然完成")
    void unrenderableReceiptsAreSkippedAndExplained() throws Exception {
        completedClaim("正常");
        UUID bad = completedClaim("版面放不下");
        UUID badReceipt = receipts.findValidByClaimId(bad).orElseThrow().getId();
        entityManager.flush();
        jdbc.update("UPDATE donation_receipts SET organization_name = ? WHERE id = ?", "機".repeat(60), badReceipt);
        entityManager.clear();
        UUID id = startExport();

        ClosingExport done = exportService.advance(id);

        assertThat(done.getStatus()).isEqualTo(ExportStatus.READY);
        assertThat(done.getSkipped()).isEqualTo(1);
        Map<String, byte[]> zip = unzip(storage.read(StorageBucket.PRIVATE, done.getObjectName()).orElseThrow());
        assertThat(zip.keySet()).contains("00_未收進壓縮檔的收據.txt", "白聯/OSE-000001.pdf")
                .doesNotContain("白聯/OSE-000002.pdf");
        assertThat(new String(zip.get("00_未收進壓縮檔的收據.txt"), StandardCharsets.UTF_8))
                .contains("OSE-000002").contains("實際收件機構");
    }

    @Test
    @DisplayName("暫存的 PDF 已被清除也能匯出：自動重新產生")
    void regeneratesMissingPdfsWhileExporting() throws Exception {
        UUID claimId = completedClaim("暫存被清掉");
        DonationReceipt r = receipts.findValidByClaimId(claimId).orElseThrow();
        receiptService().generatePdf(r.getId());
        storage.delete(StorageBucket.PRIVATE, "closing/receipts/OSE-000001/white.pdf");
        UUID id = startExport();

        ClosingExport done = exportService.advance(id);

        assertThat(done.getStatus()).isEqualTo(ExportStatus.READY);
        assertThat(unzip(storage.read(StorageBucket.PRIVATE, done.getObjectName()).orElseThrow()).keySet())
                .contains("白聯/OSE-000001.pdf");
    }

    @Test
    @DisplayName("沒有有效收據的檔期不能匯出")
    void cannotExportAnEmptyCampaign() throws Exception {
        mvc.perform(as(post("/api/admin/campaigns/{id}/receipt-exports", campaign.getId()), ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("NO_RECEIPTS"));
    }

    // ------------------------------------------------------------ API

    @Test
    @DisplayName("API：進度、advance、完成前取下載網址回 409、完成後取得網址並寫稽核")
    void apiFlow() throws Exception {
        completedClaim("API 流程");
        UUID id = startExport();

        mvc.perform(as(get("/api/admin/closing-exports/{id}/download-url", id), ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EXPORT_NOT_READY"));

        mvc.perform(as(post("/api/admin/closing-exports/{id}/advance", id), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.skipped").value(0));
        mvc.perform(as(get("/api/admin/closing-exports/{id}", id), ADMIN))
                .andExpect(jsonPath("$.status").value("READY"));

        mvc.perform(as(get("/api/admin/closing-exports/{id}/download-url", id), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.containsString(".zip")));
        assertThat(auditLogs.findAll()).extracting(log -> log.getAction()).contains(
                AdminAuditAction.CREATE_CLOSING_EXPORT, AdminAuditAction.DOWNLOAD_CLOSING_EXPORT);
    }

    @Test
    @DisplayName("只有管理員能匯出")
    void adminOnly() throws Exception {
        completedClaim("權限");
        mvc.perform(as(post("/api/admin/campaigns/{id}/receipt-exports", campaign.getId()), DONOR))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("收據下載網址：預設預覽、可指定下載；稽核紀錄分別註明")
    void receiptOpenModes() throws Exception {
        UUID claimId = completedClaim("開啟方式");
        UUID receiptId = receipts.findValidByClaimId(claimId).orElseThrow().getId();

        mvc.perform(as(get("/api/admin/receipts/{id}/download-url", receiptId).param("kind", "WHITE"), ADMIN))
                .andExpect(status().isOk());
        mvc.perform(as(get("/api/admin/receipts/{id}/download-url", receiptId).param("kind", "WHITE")
                        .param("mode", "DOWNLOAD"), ADMIN))
                .andExpect(status().isOk());

        assertThat(auditLogs.findAll()).filteredOn(log -> log.getAction() == AdminAuditAction.VIEW_RECEIPT_DOCUMENT)
                .extracting(log -> log.getDetail())
                .anyMatch(detail -> detail.startsWith("預覽收據"))
                .anyMatch(detail -> detail.startsWith("下載收據"));
    }

    @Autowired
    DonationReceiptService receiptServiceBean;

    private DonationReceiptService receiptService() {
        return receiptServiceBean;
    }
}
