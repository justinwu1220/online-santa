package com.onlinesanta.closing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.onlinesanta.attachment.AttachmentPurpose;
import com.onlinesanta.claim.dto.ReceiptRequest;
import com.onlinesanta.closing.dto.CampaignRequest;
import com.onlinesanta.common.TaiwanYear;
import com.onlinesanta.common.exception.BusinessRuleException;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.receipt.ReceiptCrypto;
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

@DisplayName("捐贈收據：完成時開立、快照、PDF 暫存與重新產生、作廢重開")
class DonationReceiptIT extends ApiIntegrationTest {

    private static final String ORG_USER = "receipt-org@example.org";
    private static final String DONOR = "receipt-donor@example.com";
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
    DonationReceiptService receiptService;

    @Autowired
    DonationReceiptRepository receipts;

    @Autowired
    ObjectStorage storage;

    @Autowired
    ReceiptCrypto crypto;

    private Organization organization;

    @BeforeEach
    void setUp() {
        organization = Organization.register("收據測試機構", "王承辦", "contact@example.org", null, null, null);
        organization.approve(null, "測試資料");
        organizations.save(organization);
        User member = User.newDonor(ORG_USER, ORG_USER);
        member.joinOrganization(organization.getId());
        users.save(member);
        users.save(User.newDonor(DONOR, "熱心民眾"));
    }

    private Campaign campaign(String permit) {
        return campaignService.create(new CampaignRequest("收據測試檔期", "115000877", permit,
                "線上聖誕老人2026聖誕禮物勸募活動", TODAY.minusDays(5), TODAY.plusDays(60),
                TODAY.minusDays(5), TODAY.plusDays(60), TODAY.minusDays(5), TODAY.plusDays(59), "OST", null, null, null));
    }

    private UUID shippedClaim(String title, boolean withReceipt) throws Exception {
        Wish wish = Wish.draft(organization, "小星", AgeRange.AGE_7_9, "畫畫", title, "描述", WishCategory.ART,
                PriceRange.UNDER_500);
        wish.publish();
        UUID wishId = wishes.save(wish).getId();
        String body = mvc.perform(as(post("/api/wishes/{id}/claim", wishId), DONOR))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID claimId = UUID.fromString(json.readTree(body).get("id").asText());
        uploadConfirmedAttachment(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);
        if (withReceipt) {
            uploadConfirmedAttachment(AttachmentPurpose.RECEIPT_PROOF, claimId, DONOR);
        }
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                        withReceipt
                                ? ShipRequests.withReceipt("郵局", "R1", new ReceiptRequest("王大明", "a123456789", "r@example.com"))
                                : ShipRequests.of("郵局", "R1")), DONOR))
                .andExpect(status().isOk());
        return claimId;
    }

    private void complete(UUID claimId) throws Exception {
        uploadConfirmedAttachment(AttachmentPurpose.ORG_FEEDBACK, claimId, ORG_USER);
        mvc.perform(as(withBody(post("/api/organizations/me/claims/{id}/complete", claimId),
                        Map.of("inspected", true)), ORG_USER))
                .andExpect(status().isOk());
    }

    private DonationReceipt validReceiptOf(UUID claimId) {
        return receipts.findValidByClaimId(claimId).orElseThrow();
    }

    private static int pages(byte[] pdf) throws Exception {
        try (PDDocument d = Loader.loadPDF(pdf)) {
            return d.getNumberOfPages();
        }
    }

    // ------------------------------------------------------------ 開立與快照

    @Test
    @DisplayName("願望完成時開立收據：配號、快照（帳號真實姓名缺就是善心人士）、時價取禮物價格")
    void completionIssuesAReceiptWithSnapshot() throws Exception {
        Campaign c = campaign("衛部救字第1150000號");
        UUID claimId = shippedClaim("要開收據", false);

        complete(claimId);

        DonationReceipt r = validReceiptOf(claimId);
        assertThat(r.getReceiptNo()).isEqualTo("OST-000001");
        assertThat(r.getCampaignId()).isEqualTo(c.getId());
        assertThat(r.getStatus()).isEqualTo(ReceiptStatus.ISSUED);
        assertThat(r.isReceiptRequested()).isFalse();
        assertThat(r.getDonorName()).isEqualTo("善心人士");
        assertThat(r.getDonorIdEncrypted()).isNull();
        assertThat(r.getGiftDescription()).isEqualTo("繪本與文具組");
        assertThat(r.getGiftQuantity()).isEqualTo(1);
        assertThat(r.getGiftUnit()).isEqualTo("份");
        assertThat(r.getGiftValue()).isEqualTo(500);
        assertThat(r.getDonatedOn()).isEqualTo(TODAY);
        assertThat(r.getOrganizationName()).isEqualTo("收據測試機構");
        assertThat(r.getPdfStatus()).isEqualTo(PdfStatus.PENDING);     // PDF 在交易提交後才產生
        assertThat(campaigns.findById(c.getId()).orElseThrow().getReceiptsIssued()).isEqualTo(1);
    }

    @Test
    @DisplayName("勾選要收據：抬頭與證號取自收據申請，證號只存密文")
    void requestedReceiptUsesTheRequestedNameAndEncryptedId() throws Exception {
        campaign("衛部救字第1150000號");
        UUID claimId = shippedClaim("要收據", true);

        complete(claimId);

        DonationReceipt r = validReceiptOf(claimId);
        assertThat(r.isReceiptRequested()).isTrue();
        assertThat(r.getDonorName()).isEqualTo("王大明");
        assertThat(r.getDonorEmail()).isEqualTo("r@example.com");
        assertThat(r.getDonorIdEncrypted()).isNotEqualTo("A123456789");
        assertThat(crypto.decrypt(r.getDonorIdEncrypted())).isEqualTo("A123456789");
    }

    @Test
    @DisplayName("沒有涵蓋捐贈日的檔期：願望照常完成，只是不開收據（列入待補）")
    void noCampaignMeansNoReceiptButCompletionStillWorks() throws Exception {
        UUID claimId = shippedClaim("沒有檔期", false);

        complete(claimId);

        assertThat(receipts.findValidByClaimId(claimId)).isEmpty();
    }

    @Test
    @DisplayName("結束的檔期不再開收據")
    void closedCampaignDoesNotIssue() throws Exception {
        Campaign c = campaign("衛部救字第1150000號");
        c.update(c.getName(), null, "衛部救字第1150000號", null, c.getCollectionStart(), c.getCollectionEnd(),
                c.getUseStart(), c.getUseEnd(), c.getWishListStart(), c.getWishListEnd(), c.getReceiptPrefix(), null, null, CampaignStatus.CLOSED);
        campaigns.saveAndFlush(c);
        UUID claimId = shippedClaim("檔期已結束", false);

        complete(claimId);

        assertThat(receipts.findValidByClaimId(claimId)).isEmpty();
    }

    @Test
    @DisplayName("連續完成的兩個願望拿到連續、不重複的編號")
    void numbersAreSequential() throws Exception {
        campaign("衛部救字第1150000號");
        UUID first = shippedClaim("第一個", false);
        UUID second = shippedClaim("第二個", false);

        complete(first);
        complete(second);

        assertThat(validReceiptOf(first).getReceiptNo()).isEqualTo("OST-000001");
        assertThat(validReceiptOf(second).getReceiptNo()).isEqualTo("OST-000002");
    }

    // ------------------------------------------------------------ PDF

    @Test
    @DisplayName("產生 PDF：紅聯 1 頁、白黃聯 2 頁，存進私有 bucket，設定到期時間；內容含編號")
    void generatesAndStoresPdf() throws Exception {
        campaign("衛部救字第1150000號");
        UUID claimId = shippedClaim("要產生", false);
        complete(claimId);
        DonationReceipt issued = validReceiptOf(claimId);

        DonationReceipt r = receiptService.generatePdf(issued.getId());

        assertThat(r.getPdfStatus()).isEqualTo(PdfStatus.READY);
        assertThat(r.getPdfExpiresAt()).isAfter(r.getPdfGeneratedAt().plusSeconds(29L * 24 * 3600));
        assertThat(r.getPermitNoUsed()).isEqualTo("衛部救字第1150000號");
        byte[] white = storage.read(StorageBucket.PRIVATE, "closing/receipts/OST-000001/red.pdf").orElseThrow();
        byte[] yellowRed = storage.read(StorageBucket.PRIVATE, "closing/receipts/OST-000001/white-yellow.pdf").orElseThrow();
        assertThat(pages(white)).isEqualTo(1);
        assertThat(pages(yellowRed)).isEqualTo(2);
        try (PDDocument d = Loader.loadPDF(white)) {
            assertThat(new PDFTextStripper().getText(d)).contains("捐贈收據").doesNotContain("待確認");
        }
    }

    @Test
    @DisplayName("舊資料檔期沒有許可文號：標失敗並說明原因、不產 PDF；填入後預覽時自動重新產生")
    void missingPermitFailsUntilFilledThenRegenerates() throws Exception {
        Campaign c = campaign(null);
        UUID claimId = shippedClaim("文號未填", false);
        complete(claimId);
        UUID receiptId = validReceiptOf(claimId).getId();

        DonationReceipt failed = receiptService.generatePdf(receiptId);
        assertThat(failed.getPdfStatus()).isEqualTo(PdfStatus.FAILED);
        assertThat(failed.getPdfError()).contains("許可文號");
        assertThat(receiptService.readPdf(receiptId, PdfKind.RED)).isEmpty();

        c = campaigns.findById(c.getId()).orElseThrow();
        c.update(c.getName(), null, "衛部救字第1150000號", null, c.getCollectionStart(), c.getCollectionEnd(),
                c.getUseStart(), c.getUseEnd(), c.getWishListStart(), c.getWishListEnd(), c.getReceiptPrefix(), null, null, CampaignStatus.ACTIVE);
        campaigns.saveAndFlush(c);

        assertThat(receiptService.readPdf(receiptId, PdfKind.RED)).isPresent();
        assertThat(receipts.findById(receiptId).orElseThrow().getPdfStatus()).isEqualTo(PdfStatus.READY);
    }

    @Test
    @DisplayName("捐贈日期是機構確認的實際收到日，不是寄出日")
    void donationDateIsTheReceivedDate() throws Exception {
        campaign("衛部救字第1150000號");
        UUID claimId = shippedClaim("收到日不同", false);
        entityManager().flush();
        jdbc().update("UPDATE claims SET shipped_on = ? WHERE id = ?", java.sql.Date.valueOf(TODAY.minusDays(4)), claimId);
        entityManager().clear();
        uploadConfirmedAttachment(AttachmentPurpose.ORG_FEEDBACK, claimId, ORG_USER);
        mvc.perform(as(withBody(post("/api/organizations/me/claims/{id}/complete", claimId),
                        Map.of("inspected", true, "receivedOn", TODAY.minusDays(1).toString())), ORG_USER))
                .andExpect(status().isOk());

        DonationReceipt r = validReceiptOf(claimId);

        assertThat(r.getDonatedOn()).isEqualTo(TODAY.minusDays(1));
    }

    @Test
    @DisplayName("舊資料沒有實際收到日：重開時退回收到時間的台北日期")
    void donationDateFallsBackToReceivedAt() throws Exception {
        campaign("衛部救字第1150000號");
        UUID claimId = shippedClaim("舊資料", false);
        complete(claimId);
        entityManager().flush();
        jdbc().update("UPDATE claims SET received_on = NULL, received_at = ? WHERE id = ?",
                java.sql.Timestamp.from(TODAY.minusDays(2).atTime(10, 0).atZone(TaiwanYear.ZONE).toInstant()), claimId);
        entityManager().clear();

        DonationReceipt replacement = receiptService.voidAndReissue(validReceiptOf(claimId).getId(), "重開測試");

        assertThat(replacement.getDonatedOn()).isEqualTo(TODAY.minusDays(2));
    }

    @Test
    @DisplayName("PDF 暫存被清除後重新產生：內容一致，且許可文號用第一次產生時凍結的值")
    void regenerationUsesTheFrozenCampaignInfo() throws Exception {
        Campaign c = campaign("衛部救字第1150000號");
        UUID claimId = shippedClaim("要重產", false);
        complete(claimId);
        UUID receiptId = validReceiptOf(claimId).getId();
        receiptService.generatePdf(receiptId);
        byte[] before = receiptService.readPdf(receiptId, PdfKind.RED).orElseThrow();

        // 之後檔期的文號被改了，已產生過的收據不受影響
        c = campaigns.findById(c.getId()).orElseThrow();
        c.update(c.getName(), null, "另一個文號", null, c.getCollectionStart(), c.getCollectionEnd(),
                c.getUseStart(), c.getUseEnd(), c.getWishListStart(), c.getWishListEnd(), c.getReceiptPrefix(), null, null, CampaignStatus.ACTIVE);
        campaigns.saveAndFlush(c);
        // 暫存到期被清除
        storage.delete(StorageBucket.PRIVATE, "closing/receipts/OST-000001/red.pdf");

        byte[] after = receiptService.readPdf(receiptId, PdfKind.RED).orElseThrow();

        assertThat(receipts.findById(receiptId).orElseThrow().getPermitNoUsed()).isEqualTo("衛部救字第1150000號");
        try (PDDocument d1 = Loader.loadPDF(before); PDDocument d2 = Loader.loadPDF(after)) {
            assertThat(new PDFTextStripper().getText(d2)).isEqualTo(new PDFTextStripper().getText(d1));
        }
        assertThat(after.length).isBetween(before.length - 2000, before.length + 2000);
    }

    @Test
    @DisplayName("版面放不下：標失敗並記原因，不裁字")
    void layoutOverflowIsRecordedAsFailed() throws Exception {
        campaign("衛部救字第1150000號");
        UUID claimId = shippedClaim("版面失敗", true);
        complete(claimId);
        // 把機構名稱改到放不進「實際收件機構」欄（快照欄位不可改，這裡直接用 SQL 模擬極端資料）
        UUID receiptId = validReceiptOf(claimId).getId();
        entityManager().flush();
        jdbc().update("UPDATE donation_receipts SET organization_name = ? WHERE id = ?", "機".repeat(60), receiptId);
        entityManager().clear();

        DonationReceipt r = receiptService.generatePdf(receiptId);

        assertThat(r.getPdfStatus()).isEqualTo(PdfStatus.FAILED);
        assertThat(r.getPdfError()).contains("實際收件機構");
    }

    @Test
    @DisplayName("證號解不開（金鑰不符或資料毀損）：標失敗並說明原因，不丟未預期錯誤")
    void undecryptableIdNumberIsRecordedAsFailed() throws Exception {
        campaign("衛部救字第1150000號");
        UUID claimId = shippedClaim("證號解不開", true);
        complete(claimId);
        UUID receiptId = validReceiptOf(claimId).getId();
        entityManager().flush();
        jdbc().update("UPDATE donation_receipts SET donor_id_encrypted = ? WHERE id = ?",
                java.util.Base64.getEncoder().encodeToString(new byte[40]), receiptId);
        entityManager().clear();

        DonationReceipt r = receiptService.generatePdf(receiptId);

        assertThat(r.getPdfStatus()).isEqualTo(PdfStatus.FAILED);
        assertThat(r.getPdfError()).contains("捐贈者證號").contains("無法解密");
    }

    // ------------------------------------------------------------ 作廢重開

    @Test
    @DisplayName("作廢重開：舊號保留並指向新收據，新收據拿新號碼；舊的不能再下載")
    void voidAndReissue() throws Exception {
        campaign("衛部救字第1150000號");
        UUID claimId = shippedClaim("要作廢", false);
        complete(claimId);
        DonationReceipt old = validReceiptOf(claimId);
        receiptService.generatePdf(old.getId());

        DonationReceipt replacement = receiptService.voidAndReissue(old.getId(), "捐贈者姓名填錯");

        DonationReceipt reloadedOld = receipts.findById(old.getId()).orElseThrow();
        assertThat(reloadedOld.getStatus()).isEqualTo(ReceiptStatus.VOIDED);
        assertThat(reloadedOld.getVoidReason()).isEqualTo("捐贈者姓名填錯");
        assertThat(reloadedOld.getReplacedById()).isEqualTo(replacement.getId());
        assertThat(replacement.getReceiptNo()).isEqualTo("OST-000002");
        assertThat(validReceiptOf(claimId).getId()).isEqualTo(replacement.getId());
        assertThatThrownBy(() -> receiptService.readPdf(old.getId(), PdfKind.RED))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("已作廢");
        // 作廢的 PDF 檔案一併清掉
        assertThat(storage.read(StorageBucket.PRIVATE, "closing/receipts/OST-000001/red.pdf")).isEmpty();
    }

    @Test
    @DisplayName("作廢必須填原因；重開不了（沒有涵蓋的檔期）就整個回滾，舊收據維持有效")
    void voidRulesAndAtomicity() throws Exception {
        Campaign c = campaign("衛部救字第1150000號");
        UUID claimId = shippedClaim("作廢規則", false);
        complete(claimId);
        DonationReceipt old = validReceiptOf(claimId);

        assertThatThrownBy(() -> receiptService.voidAndReissue(old.getId(), " "))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("作廢原因");

        c = campaigns.findById(c.getId()).orElseThrow();
        c.update(c.getName(), null, "衛部救字第1150000號", null, c.getCollectionStart(), c.getCollectionEnd(),
                c.getUseStart(), c.getUseEnd(), c.getWishListStart(), c.getWishListEnd(), c.getReceiptPrefix(), null, null, CampaignStatus.CLOSED);
        campaigns.saveAndFlush(c);
        assertThatThrownBy(() -> receiptService.voidAndReissue(old.getId(), "想重開"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("無法重開");
    }

    @Autowired
    jakarta.persistence.EntityManager entityManager;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    private jakarta.persistence.EntityManager entityManager() {
        return entityManager;
    }

    private org.springframework.jdbc.core.JdbcTemplate jdbc() {
        return jdbc;
    }
}
