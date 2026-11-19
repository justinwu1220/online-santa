package com.onlinesanta.closing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.TestTransaction;

import com.onlinesanta.attachment.AttachmentPurpose;
import com.onlinesanta.closing.dto.CampaignRequest;
import com.onlinesanta.common.TaiwanYear;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
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

/**
 * 願望完成、交易真的提交之後，收據 PDF 會自動產生並寫成「已產生」。
 *
 * <p>其他整合測試都在會回滾的交易裡跑，AFTER_COMMIT 不會觸發，所以抓不到「PDF 傳上去了、READY 狀態卻沒存進資料庫」
 * 這一類問題（監聽器沿用已提交的交易時就會這樣）。這裡用 {@link TestTransaction} 真的提交一次，測完手動清掉資料。
 */
@DisplayName("收據開立後自動產生 PDF（真的提交交易）")
class ReceiptAutoPdfIT extends ApiIntegrationTest {

    private static final String ORG_NAME = "自動PDF測試機構";
    private static final String ORG_USER = "autopdf-org@example.org";
    private static final String DONOR = "autopdf-donor@example.com";
    private static final String PREFIX = "AUTOPDF";
    private static final LocalDate TODAY = LocalDate.now(TaiwanYear.ZONE);

    @Autowired OrganizationRepository organizations;
    @Autowired UserRepository users;
    @Autowired WishRepository wishes;
    @Autowired CampaignService campaignService;
    @Autowired DonationReceiptRepository receipts;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("完成願望並提交後，收據的 PDF 自動變成已產生，雲端有兩個檔案")
    void pdfIsGeneratedAutomaticallyAfterCommit() throws Exception {
        try {
            Organization organization = Organization.register(ORG_NAME, "王承辦", "contact@example.org", null, null, null);
            organization.approve(null, "測試資料");
            organizations.save(organization);
            User member = User.newDonor(ORG_USER, ORG_USER);
            member.joinOrganization(organization.getId());
            users.save(member);
            users.save(User.newDonor(DONOR, "熱心民眾"));
            campaignService.create(new CampaignRequest("自動PDF檔期", "115000877", "衛部救字第1150000號",
                    "線上聖誕老人2026聖誕禮物勸募活動", TODAY.minusDays(5), TODAY.plusDays(60),
                    TODAY.minusDays(5), TODAY.plusDays(60), TODAY.minusDays(5), TODAY.plusDays(59), PREFIX, null, null, null));

            Wish wish = Wish.draft(organization, "小星", AgeRange.AGE_7_9, "畫畫", "自動PDF", "描述", WishCategory.ART,
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

            // 提交前：收據已開立，PDF 還沒產生（AFTER_COMMIT 還沒觸發）
            assertThat(receipts.findValidByClaimId(claimId).orElseThrow().getPdfStatus()).isEqualTo(PdfStatus.PENDING);

            TestTransaction.flagForCommit();
            TestTransaction.end();          // 提交 → 監聽器在新交易裡產生 PDF
            TestTransaction.start();

            DonationReceipt receipt = receipts.findValidByClaimId(claimId).orElseThrow();
            assertThat(receipt.getPdfStatus()).isEqualTo(PdfStatus.READY);
            assertThat(receipt.getPdfError()).isNull();
            assertThat(storage.find(StorageBucket.PRIVATE, receipt.getRedObject())).isPresent();
            assertThat(storage.find(StorageBucket.PRIVATE, receipt.getWhiteYellowObject())).isPresent();
        } finally {
            cleanUp();
        }
    }

    private void cleanUp() {
        if (!TestTransaction.isActive()) {
            TestTransaction.start();
        }
        String claims = "SELECT c.id FROM claims c JOIN wishes w ON w.id = c.wish_id JOIN organizations o ON o.id = w.organization_id WHERE o.name = ?";
        jdbc.update("DELETE FROM donation_receipts WHERE campaign_id IN (SELECT id FROM campaigns WHERE receipt_prefix = ?)", PREFIX);
        jdbc.update("DELETE FROM closing_exports WHERE campaign_id IN (SELECT id FROM campaigns WHERE receipt_prefix = ?)", PREFIX);
        jdbc.update("DELETE FROM attachments WHERE owner_id IN (" + claims + ")", ORG_NAME);
        jdbc.update("DELETE FROM claim_receipts WHERE claim_id IN (" + claims + ")", ORG_NAME);
        jdbc.update("DELETE FROM messages WHERE claim_id IN (" + claims + ")", ORG_NAME);
        jdbc.update("DELETE FROM claim_events WHERE claim_id IN (" + claims + ")", ORG_NAME);
        jdbc.update("DELETE FROM claims WHERE wish_id IN (SELECT id FROM wishes WHERE organization_id = (SELECT id FROM organizations WHERE name = ?))", ORG_NAME);
        jdbc.update("DELETE FROM wish_events WHERE wish_id IN (SELECT id FROM wishes WHERE organization_id = (SELECT id FROM organizations WHERE name = ?))", ORG_NAME);
        jdbc.update("DELETE FROM wishes WHERE organization_id = (SELECT id FROM organizations WHERE name = ?)", ORG_NAME);
        jdbc.update("DELETE FROM campaigns WHERE receipt_prefix = ?", PREFIX);
        jdbc.update("DELETE FROM users WHERE email IN (?, ?)", ORG_USER, DONOR);
        jdbc.update("DELETE FROM organizations WHERE name = ?", ORG_NAME);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();            // 留一個交易給測試框架收尾
    }
}
