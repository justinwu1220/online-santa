package com.onlinesanta.closing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;

import com.onlinesanta.attachment.AttachmentPurpose;
import com.onlinesanta.claim.dto.ReceiptRequest;
import com.onlinesanta.closing.dto.CampaignRequest;
import com.onlinesanta.common.TaiwanYear;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.RecordingMailSender;
import com.onlinesanta.support.ShipRequests;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishCategory;
import com.onlinesanta.wish.WishRepository;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;

@DisplayName("結案寄信（紅聯收據、機構確認表）")
class ClosingMailIT extends ApiIntegrationTest {

    private static final String ADMIN = "platform-admin@example.com";
    private static final String ORG_USER = "mail-org@example.org";
    private static final String DONOR = "mail-donor@example.com";
    private static final LocalDate TODAY = LocalDate.now(TaiwanYear.ZONE);

    @Autowired OrganizationRepository organizations;
    @Autowired UserRepository users;
    @Autowired WishRepository wishes;
    @Autowired CampaignService campaignService;
    @Autowired DonationReceiptRepository receipts;
    @Autowired DonationReceiptService receiptService;
    @Autowired JavaMailSender mailSender;

    private RecordingMailSender mails;
    private Organization organization;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        mails = (RecordingMailSender) mailSender;
        mails.reset();
        organization = Organization.register("快樂兒童之家", "王承辦", "contact@example.org", null, null, null);
        organization.approve(null, "測試資料");
        organizations.save(organization);
        User member = User.newDonor(ORG_USER, ORG_USER);
        member.joinOrganization(organization.getId());
        users.save(member);
        users.save(User.newDonor(DONOR, "熱心民眾"));
        campaign = campaignService.create(new CampaignRequest("寄信檔期", "115000877", "衛部救字第1150000號",
                "線上聖誕老人2026聖誕禮物勸募活動", TODAY.minusDays(5), TODAY.plusDays(60),
                TODAY.minusDays(5), TODAY.plusDays(90), TODAY.minusDays(5), TODAY.plusDays(59), "OSM", null, null, null));
    }

    private void completedClaim(String title) throws Exception {
        Wish wish = Wish.draft(organization, "小星", AgeRange.AGE_7_9, "畫畫", title, "描述", WishCategory.ART,
                PriceRange.UNDER_500);
        wish.publish();
        UUID wishId = wishes.save(wish).getId();
        String body = mvc.perform(as(post("/api/wishes/{id}/claim", wishId), DONOR))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID claimId = UUID.fromString(json.readTree(body).get("id").asText());
        uploadConfirmedAttachment(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);
        uploadConfirmedAttachment(AttachmentPurpose.RECEIPT_PROOF, claimId, DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                        ShipRequests.withReceipt("郵局", "R1", new ReceiptRequest("王大明", "a123456789", "r@example.com"))), DONOR))
                .andExpect(status().isOk());
        uploadConfirmedAttachment(AttachmentPurpose.ORG_FEEDBACK, claimId, ORG_USER);
        mvc.perform(as(withBody(post("/api/organizations/me/claims/{id}/complete", claimId),
                        Map.of("inspected", true)), ORG_USER)).andExpect(status().isOk());
    }

    private static List<String> attachmentNames(RecordingMailSender.SentMail mail) throws Exception {
        List<String> names = new ArrayList<>();
        collect(mail.mime(), names);
        return names;
    }

    private static void collect(Part part, List<String> names) throws Exception {
        if (part.getFileName() != null) {
            names.add(jakarta.mail.internet.MimeUtility.decodeText(part.getFileName()));
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                collect(child, names);
            }
        }
    }

    private void generateAllPdfs() {
        for (DonationReceipt r : receipts.findAllByCampaignIdOrderByReceiptNoAsc(campaign.getId())) {
            receiptService.generatePdf(r.getId());
        }
    }

    @Test
    @DisplayName("紅聯：PDF 還沒產生不寄；產生後寄出並附紅聯 PDF；已寄的不重寄；預覽不寄信，並列出每一張的狀態")
    void redCopiesAreSentOnceWithAttachment() throws Exception {
        completedClaim("第一個");
        completedClaim("第二個");
        String base = "/api/admin/campaigns/" + campaign.getId() + "/closing-mail";

        mvc.perform(as(get(base + "/receipts"), ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.notReady").value(2))
                .andExpect(jsonPath("$.pending").value(0))
                // 預覽名單：一張收據一列，帶編號、收件信箱與狀態
                .andExpect(jsonPath("$.targets.length()").value(2))
                .andExpect(jsonPath("$.targets[0].reference").value("OSM-000001"))
                .andExpect(jsonPath("$.targets[0].recipient").value("r@example.com"))
                .andExpect(jsonPath("$.targets[0].status").value("NOT_READY"));
        mvc.perform(as(post(base + "/receipts/send"), ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(0));
        assertThat(mails.sentMails()).isEmpty();

        generateAllPdfs();
        mvc.perform(as(get(base + "/receipts"), ADMIN)).andExpect(jsonPath("$.pending").value(2))
                .andExpect(jsonPath("$.targets[0].status").value("PENDING"));
        assertThat(mails.sentMails()).isEmpty();                                  // 預覽不寄

        mvc.perform(as(post(base + "/receipts/send"), ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(2)).andExpect(jsonPath("$.pending").value(0))
                .andExpect(jsonPath("$.failed").value(0));

        assertThat(mails.sentMails()).hasSize(2);
        mvc.perform(as(get(base + "/receipts"), ADMIN))
                .andExpect(jsonPath("$.targets[0].status").value("SENT"));
        assertThat(mails.sent()).allSatisfy(m -> assertThat(m.getTo()).containsExactly("r@example.com"));
        assertThat(mails.sent().get(0).getSubject()).contains("您的捐贈收據（OSM-000001）".replace("OSM", "OSM"));
        assertThat(attachmentNames(mails.sentMails().get(0))).containsExactly("捐贈收據-OSM-000001.pdf");
        assertThat(receipts.findAllByCampaignIdOrderByReceiptNoAsc(campaign.getId()))
                .allSatisfy(r -> assertThat(r.getRedSentAt()).isNotNull());

        mvc.perform(as(post(base + "/receipts/send"), ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(2));
        assertThat(mails.sentMails()).hasSize(2);                                 // 沒有重寄
    }

    @Test
    @DisplayName("機構確認表：每個外部機構一封，附 05A 與 05B；寄過不重寄；紀錄留下收件人")
    void orgFormsAreSentOncePerOrganization() throws Exception {
        completedClaim("第一個");
        completedClaim("第二個");
        mails.reset();
        String base = "/api/admin/campaigns/" + campaign.getId() + "/closing-mail";

        mvc.perform(as(get(base + "/org-forms"), ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.pending").value(1))
                .andExpect(jsonPath("$.targets[0].name").value("快樂兒童之家"))
                .andExpect(jsonPath("$.targets[0].reference").doesNotExist());   // 機構確認表沒有收據編號
        assertThat(mails.sentMails()).isEmpty();

        mvc.perform(as(post(base + "/org-forms/send"), ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(1)).andExpect(jsonPath("$.pending").value(0));

        assertThat(mails.sentMails()).hasSize(1);
        assertThat(mails.sent().get(0).getTo()).containsExactly("contact@example.org");
        assertThat(attachmentNames(mails.sentMails().get(0)))
                .containsExactly("05A_機構物資領受暨發放確認表.pdf", "05B_個人物資領受證明.pdf");

        mvc.perform(as(post(base + "/org-forms/send"), ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(1));
        assertThat(mails.sentMails()).hasSize(1);
    }
}
