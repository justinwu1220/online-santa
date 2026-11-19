package com.onlinesanta.closing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.onlinesanta.attachment.AttachmentPurpose;
import com.onlinesanta.claim.dto.ReceiptRequest;
import com.onlinesanta.closing.dto.CampaignRequest;
import com.onlinesanta.closing.dto.ReceiptCheckView;
import com.onlinesanta.closing.dto.ReceiptCheckView.Issue;
import com.onlinesanta.common.TaiwanYear;
import com.onlinesanta.job.dto.ClosingCleanupResult;
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

@DisplayName("結案文件：收據檢查與暫存檔到期清除")
class ClosingMaintenanceIT extends ApiIntegrationTest {

    private static final String ADMIN = "platform-admin@example.com";
    private static final String ORG_USER = "maint-org@example.org";
    private static final String DONOR = "maint-donor@example.com";
    private static final LocalDate TODAY = LocalDate.now(TaiwanYear.ZONE);

    @Autowired OrganizationRepository organizations;
    @Autowired UserRepository users;
    @Autowired WishRepository wishes;
    @Autowired CampaignService campaignService;
    @Autowired DonationReceiptService receiptService;
    @Autowired DonationReceiptRepository receipts;
    @Autowired ReceiptCheckService check;
    @Autowired ClosingExportService exportService;
    @Autowired ClosingExportRepository exportRepository;
    @Autowired ClosingCleanupService cleanup;
    @Autowired ObjectStorage storage;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    private Organization organization;

    @BeforeEach
    void setUp() {
        organization = Organization.register("維護測試機構", "王承辦", "contact@example.org", null, null, null);
        organization.approve(null, "測試資料");
        organizations.save(organization);
        User member = User.newDonor(ORG_USER, ORG_USER);
        member.joinOrganization(organization.getId());
        users.save(member);
        users.save(User.newDonor(DONOR, "熱心民眾"));
    }

    private Campaign campaign(String permit) {
        return campaignService.create(new CampaignRequest("維護檔期", "115000877", permit,
                "線上聖誕老人2026聖誕禮物勸募活動", TODAY.minusDays(5), TODAY.plusDays(60),
                TODAY.minusDays(5), TODAY.plusDays(60), TODAY.minusDays(5), TODAY.plusDays(59), "OSM", null, null, null));
    }

    private UUID completedClaim(String title, boolean withProof) throws Exception {
        Wish wish = Wish.draft(organization, "小星", AgeRange.AGE_7_9, "畫畫", title, "描述", WishCategory.ART,
                PriceRange.UNDER_500);
        wish.publish();
        UUID wishId = wishes.save(wish).getId();
        String body = mvc.perform(as(post("/api/wishes/{id}/claim", wishId), DONOR))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID claimId = UUID.fromString(json.readTree(body).get("id").asText());
        uploadConfirmedAttachment(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);
        if (withProof) {
            uploadConfirmedAttachment(AttachmentPurpose.RECEIPT_PROOF, claimId, DONOR);
        }
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                        withProof ? ShipRequests.withReceipt("郵局", "R1", new ReceiptRequest("王大明", "a123456789", "r@example.com"))
                                : ShipRequests.of("郵局", "R1")), DONOR)).andExpect(status().isOk());
        uploadConfirmedAttachment(AttachmentPurpose.ORG_FEEDBACK, claimId, ORG_USER);
        mvc.perform(as(withBody(post("/api/organizations/me/claims/{id}/complete", claimId),
                        Map.of("inspected", true)), ORG_USER)).andExpect(status().isOk());
        return claimId;
    }

    private static boolean has(ReceiptCheckView view, String code) {
        return view.issues().stream().anyMatch(i -> i.code().equals(code));
    }

    private void sql(String sql, Object... args) {
        entityManager.flush();
        jdbc.update(sql, args);
        entityManager.clear();
    }

    // ------------------------------------------------------------ 收據檢查

    @Test
    @DisplayName("一切正常：張數與時價合計正確，沒有需處理的項目；沒有發票佐證只是提醒")
    void cleanCampaignPassesAndMissingEvidenceIsOnlyAWarning() throws Exception {
        Campaign campaign = campaign("衛部救字第1150000號");
        UUID withProof = completedClaim("有發票", true);
        UUID withoutProof = completedClaim("沒發票", false);
        receiptService.generatePdf(receipts.findValidByClaimId(withProof).orElseThrow().getId());
        receiptService.generatePdf(receipts.findValidByClaimId(withoutProof).orElseThrow().getId());

        ReceiptCheckView view = check.check(campaign.getId());

        assertThat(view.completedWishes()).isEqualTo(2);
        assertThat(view.validReceipts()).isEqualTo(2);
        assertThat(view.pdfReady()).isEqualTo(2);
        assertThat(view.totalValue()).isPositive();
        assertThat(view.passed()).isTrue();
        assertThat(view.issues()).extracting(Issue::code).containsExactly("NO_EVIDENCE");
        assertThat(view.issues().get(0).receiptNo()).isEqualTo(receipts.findValidByClaimId(withoutProof).orElseThrow().getReceiptNo());
        assertThat(view.toText()).contains("有效收據：2 張").contains("白聯、黃紅聯資料夾各 2 檔");
    }

    @Test
    @DisplayName("完成了卻沒有收據：列為需處理並說明原因（檔期是事後才建立的）")
    void completedWithoutReceiptIsAnError() throws Exception {
        completedClaim("檔期前就完成", false);          // 還沒有檔期，所以沒開收據
        Campaign campaign = campaign("衛部救字第1150000號");

        ReceiptCheckView view = check.check(campaign.getId());

        assertThat(view.completedWishes()).isEqualTo(1);
        assertThat(view.validReceipts()).isZero();
        assertThat(view.passed()).isFalse();
        assertThat(view.issues()).anyMatch(i -> i.code().equals("MISSING_RECEIPT")
                && i.message().contains("檔期前就完成") && i.message().contains("開立失敗"));
    }

    @Test
    @DisplayName("舊資料沒有許可文號與版面失敗：都列為需處理，附原因")
    void pdfProblemsAreReported() throws Exception {
        Campaign campaign = campaign(null);
        UUID claimId = completedClaim("缺文號", true);
        receiptService.generatePdf(receipts.findValidByClaimId(claimId).orElseThrow().getId());

        ReceiptCheckView noPermit = check.check(campaign.getId());
        assertThat(noPermit.pdfFailed()).isEqualTo(1);
        assertThat(noPermit.issues()).anyMatch(i -> i.code().equals("PDF_FAILED") && i.message().contains("許可文號"));

        UUID id = receipts.findValidByClaimId(claimId).orElseThrow().getId();
        sql("UPDATE campaigns SET permit_no = ? WHERE id = ?", "衛部救字第1150000號", campaign.getId());
        sql("UPDATE donation_receipts SET organization_name = ? WHERE id = ?", "機".repeat(60), id);
        receiptService.generatePdf(id);

        ReceiptCheckView failed = check.check(campaign.getId());
        assertThat(failed.pdfFailed()).isEqualTo(1);
        assertThat(failed.issues()).anyMatch(i -> i.code().equals("PDF_FAILED") && i.message().contains("實際收件機構"));
    }

    @Test
    @DisplayName("編號中間缺號：列為需處理並指出缺的流水號；作廢的號碼不算缺")
    void numberGapsAreFoundButVoidedNumbersAreNot() throws Exception {
        Campaign campaign = campaign("衛部救字第1150000號");
        UUID first = completedClaim("一", true);
        UUID second = completedClaim("二", true);
        UUID third = completedClaim("三", true);

        // 作廢第二張並重開：原號碼仍在資料裡，不算缺
        DonationReceipt secondReceipt = receipts.findValidByClaimId(second).orElseThrow();
        receiptService.voidAndReissue(secondReceipt.getId(), "姓名填錯");
        assertThat(has(check.check(campaign.getId()), "NUMBER_GAP")).isFalse();

        // 直接刪掉第一張（資料被刪才會缺號）
        UUID firstId = receipts.findValidByClaimId(first).orElseThrow().getId();
        sql("DELETE FROM donation_receipts WHERE id = ?", firstId);
        // 刪掉最小號碼不算缺號（中間沒有空洞）；改刪第三張之前的中間號
        assertThat(has(check.check(campaign.getId()), "NUMBER_GAP")).isFalse();
        UUID thirdId = receipts.findValidByClaimId(third).orElseThrow().getId();
        sql("DELETE FROM donation_receipts WHERE id = ?", thirdId);
        // 現在剩 2（作廢）與 4（重開），缺 3
        ReceiptCheckView view = check.check(campaign.getId());
        assertThat(view.issues()).anyMatch(i -> i.code().equals("NUMBER_GAP") && i.message().contains("3"));
    }

    @Test
    @DisplayName("匯出的壓縮檔含收據檢查報告")
    void exportContainsTheCheckReport() throws Exception {
        Campaign campaign = campaign("衛部救字第1150000號");
        completedClaim("一張", true);
        String body = mvc.perform(as(post("/api/admin/campaigns/{id}/receipt-exports", campaign.getId()), ADMIN))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID exportId = UUID.fromString(json.readTree(body).get("id").asText());

        ClosingExport done = exportService.advance(exportId);

        byte[] zip = storage.read(StorageBucket.PRIVATE, done.getObjectName()).orElseThrow();
        try (var in = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zip), java.nio.charset.StandardCharsets.UTF_8)) {
            for (var e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                if (e.getName().equals("00_收據檢查.txt")) {
                    assertThat(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).contains("有效收據：1 張");
                    return;
                }
            }
        }
        throw new AssertionError("壓縮檔裡沒有 00_收據檢查.txt");
    }

    @Test
    @DisplayName("API：管理員取得檢查結果，其他人 403")
    void apiAccess() throws Exception {
        Campaign campaign = campaign("衛部救字第1150000號");
        completedClaim("API", true);

        mvc.perform(as(get("/api/admin/campaigns/{id}/receipt-check", campaign.getId()), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validReceipts").value(1))
                .andExpect(jsonPath("$.completedWishes").value(1));
        mvc.perform(as(get("/api/admin/campaigns/{id}/receipt-check", campaign.getId()), DONOR))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ 到期清除

    @Test
    @DisplayName("過了暫存期限的收據 PDF 與匯出壓縮檔被清除；資料保留，收據之後重新產生，沒過期的不動")
    void expiredFilesAreClearedAndReceiptsRegenerate() throws Exception {
        Campaign campaign = campaign("衛部救字第1150000號");
        UUID oldClaim = completedClaim("過期的", true);
        UUID freshClaim = completedClaim("沒過期的", true);
        DonationReceipt old = receipts.findValidByClaimId(oldClaim).orElseThrow();
        DonationReceipt fresh = receipts.findValidByClaimId(freshClaim).orElseThrow();
        receiptService.generatePdf(old.getId());
        receiptService.generatePdf(fresh.getId());
        String oldWhite = receipts.findById(old.getId()).orElseThrow().getWhiteObject();
        String freshWhite = receipts.findById(fresh.getId()).orElseThrow().getWhiteObject();

        String exportBody = mvc.perform(as(post("/api/admin/campaigns/{id}/receipt-exports", campaign.getId()), ADMIN))
                .andReturn().getResponse().getContentAsString();
        UUID exportId = UUID.fromString(json.readTree(exportBody).get("id").asText());
        ClosingExport export = exportService.advance(exportId);
        String zipObject = export.getObjectName();

        Instant past = Instant.now().minus(Duration.ofDays(1));
        sql("UPDATE donation_receipts SET pdf_expires_at = ? WHERE id = ?", java.sql.Timestamp.from(past), old.getId());
        sql("UPDATE closing_exports SET expires_at = ? WHERE id = ?", java.sql.Timestamp.from(past), exportId);

        ClosingCleanupResult result = cleanup.cleanup();

        assertThat(result.receiptsFound()).isEqualTo(1);
        assertThat(result.receiptsCleared()).isEqualTo(1);
        assertThat(result.exportsCleared()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(storage.find(StorageBucket.PRIVATE, oldWhite)).isEmpty();
        assertThat(storage.find(StorageBucket.PRIVATE, zipObject)).isEmpty();
        assertThat(storage.find(StorageBucket.PRIVATE, freshWhite)).isPresent();
        DonationReceipt cleared = receipts.findById(old.getId()).orElseThrow();
        assertThat(cleared.getPdfStatus()).isEqualTo(PdfStatus.PENDING);
        assertThat(cleared.getReceiptNo()).isEqualTo(old.getReceiptNo());          // 資料還在
        assertThat(exportRepository.findById(exportId).orElseThrow().getStatus()).isEqualTo(ExportStatus.EXPIRED);

        // 需要時重新產生，編號與內容不變
        assertThat(receiptService.ensurePdfObject(old.getId(), PdfKind.WHITE)).isPresent();
        assertThat(receipts.findById(old.getId()).orElseThrow().getPdfStatus()).isEqualTo(PdfStatus.READY);

        // 再跑一次沒有東西可清
        ClosingCleanupResult again = cleanup.cleanup();
        assertThat(again.receiptsFound()).isZero();
        assertThat(again.exportsFound()).isZero();
    }
}
