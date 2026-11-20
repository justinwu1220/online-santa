package com.onlinesanta.closing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
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

import com.onlinesanta.attachment.AttachmentPurpose;
import com.onlinesanta.claim.dto.ReceiptRequest;
import com.onlinesanta.closing.doc.ClosingStage;
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

@DisplayName("結案文件包（C 成果備查、D 結案備查、併報）")
class ClosingPackageIT extends ApiIntegrationTest {

    private static final String ADMIN = "platform-admin@example.com";
    private static final String ORG_USER = "pack-org@example.org";
    private static final String DONOR = "pack-donor@example.com";
    private static final LocalDate TODAY = LocalDate.now(TaiwanYear.ZONE);

    @Autowired OrganizationRepository organizations;
    @Autowired UserRepository users;
    @Autowired WishRepository wishes;
    @Autowired CampaignService campaignService;
    @Autowired DonationReceiptRepository receipts;
    @Autowired ClosingExportService exportService;
    @Autowired ObjectStorage storage;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    private Organization organization;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        organization = Organization.register("快樂兒童之家", "王承辦", "contact@example.org", null, null, null);
        organization.approve(null, "測試資料");
        organizations.save(organization);
        User member = User.newDonor(ORG_USER, ORG_USER);
        member.joinOrganization(organization.getId());
        users.save(member);
        users.save(User.newDonor(DONOR, "熱心民眾"));
        campaign = campaignService.create(new CampaignRequest("套件檔期", "115000877", "衛部救字第1150000號",
                "線上聖誕老人2026聖誕禮物勸募活動", TODAY.minusDays(5), TODAY.plusDays(60),
                TODAY.minusDays(5), TODAY.plusDays(90), TODAY.minusDays(5), TODAY.plusDays(59), "OSK", null, null, null));
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
        uploadConfirmedAttachment(AttachmentPurpose.RECEIPT_PROOF, claimId, DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                        ShipRequests.withReceipt("郵局", "R1", new ReceiptRequest("王大明", "a123456789", "r@example.com"))), DONOR))
                .andExpect(status().isOk());
        uploadConfirmedAttachment(AttachmentPurpose.ORG_FEEDBACK, claimId, ORG_USER);
        mvc.perform(as(withBody(post("/api/organizations/me/claims/{id}/complete", claimId),
                        Map.of("inspected", true)), ORG_USER)).andExpect(status().isOk());
        return claimId;
    }

    private Map<String, byte[]> buildPackage(ClosingStage stage) throws Exception {
        String body = mvc.perform(as(post("/api/admin/campaigns/{id}/closing-packages", campaign.getId())
                        .param("stage", stage.name()), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("CLOSING_PACKAGE"))
                .andExpect(jsonPath("$.stage").value(stage.name()))
                .andReturn().getResponse().getContentAsString();
        UUID exportId = UUID.fromString(json.readTree(body).get("id").asText());
        ClosingExport done = exportService.advance(exportId);
        assertThat(done.getStatus()).isEqualTo(ExportStatus.READY);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(
                storage.read(StorageBucket.PRIVATE, done.getObjectName()).orElseThrow()), StandardCharsets.UTF_8)) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                entries.put(e.getName(), in.readAllBytes());
            }
        }
        return entries;
    }

    private static String sheetOf(byte[] xlsx) throws Exception {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(xlsx), StandardCharsets.UTF_8)) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                if (e.getName().equals("xl/worksheets/sheet1.xml")) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("沒有工作表");
    }

    private static byte[] unzipEntry(byte[] zip, String name) throws Exception {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                if (e.getName().equals(name)) {
                    return in.readAllBytes();
                }
            }
        }
        throw new AssertionError("沒有 " + name);
    }

    @Test
    @DisplayName("本活動沒有現金：不需要輸入財務資料，三個階段的現金、利息、自籌都是 0，待補資料沒有現金類項目")
    void cashIsAlwaysZero() throws Exception {
        completedClaim("第一個");

        for (ClosingStage stage : ClosingStage.values()) {
            Map<String, byte[]> zip = buildPackage(stage);

            String todo = new String(zip.get("05_留存備份/待補資料.md"), StandardCharsets.UTF_8);
            assertThat(todo).as(stage.name()).doesNotContain("現金").doesNotContain("利息").doesNotContain("自籌")
                    .doesNotContain("募得款實支");
            String cash = new String(unzipEntry(zip.get("05_留存備份/結案登記簿.xlsx"), "xl/worksheets/sheet3.xml"), StandardCharsets.UTF_8);
            assertThat(cash).as(stage.name()).doesNotContain("待補").contains("本活動沒有現金捐款");

            String page = new String(zip.entrySet().stream().filter(e -> e.getKey().endsWith("00_照表操作.html"))
                    .findFirst().orElseThrow().getValue(), StandardCharsets.UTF_8);
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(page);
            String p = stage == ClosingStage.CD ? "cd" : stage.name().toLowerCase();
            for (String id : java.util.List.of(p + ".interest", p + ".totals.interest", p + ".totals.balance", p + ".totals.self")) {
                assertThat(doc.getElementById(id).attr("value")).as(id).isEqualTo("0");
                assertThat(doc.getElementById(id).attr("data-ready")).as(id).isEqualTo("true");
            }
            assertThat(doc.getElementById(p + ".income").attr("data-ready")).isEqualTo("true");
        }
    }

    private static int pages(byte[] pdf) throws Exception {
        try (PDDocument d = Loader.loadPDF(pdf)) {
            return d.getNumberOfPages();
        }
    }

    @Test
    @DisplayName("C 成果備查：捐物人資料 Excel 與核章 PDF、收據兩個資料夾、檢查報告與待補資料")
    void stageC() throws Exception {
        completedClaim("第一個");
        completedClaim("第二個");

        Map<String, byte[]> zip = buildPackage(ClosingStage.C);

        String dir = "02_交給康乃心/C.成果備查/";
        assertThat(zip.keySet()).contains(dir + "02_捐物人資料.xlsx", dir + "02_捐物人資料.pdf",
                "03_收據/紅聯（給捐贈人）/OSK-000001.pdf", "03_收據/白黃聯（協會留存）/OSK-000002.pdf",
                "05_留存備份/00_收據檢查.txt", "05_留存備份/00_收據清單.csv", "05_留存備份/待補資料.md");
        assertThat(zip.keySet()).noneMatch(n -> n.contains("支出明細"));        // C 沒有支出明細
        assertThat(zip.keySet()).contains("00_開始這裡.html", dir + "00_照表操作.html",
                "02_交給康乃心/Facebook公開徵信/00_發文說明.html",
                "02_交給康乃心/Facebook公開徵信/C_勸募期滿/01_貼文文字.txt",
                "02_交給康乃心/Facebook公開徵信/C_勸募期滿/02_徵信圖片/001.jpg");
        String operation = new String(zip.get(dir + "00_照表操作.html"), StandardCharsets.UTF_8);
        assertThat(operation).contains("c.items.0.name").contains("OSK-000001");
        // 每個外部機構一份 05A 與一份 05B（兩份都不含個人資料，可以安全寄給機構）；沒有指定協會自身服務對象就沒有 05C
        assertThat(zip.keySet()).contains("04_機構確認/待簽章/001_快樂兒童之家/05A_機構物資領受暨發放確認表.pdf",
                "04_機構確認/待簽章/001_快樂兒童之家/05B_個人物資領受證明_機構留存.pdf");
        assertThat(zip.keySet()).noneMatch(n -> n.contains("05C"));
        assertThat(pages(zip.get("04_機構確認/待簽章/001_快樂兒童之家/05B_個人物資領受證明_機構留存.pdf"))).isEqualTo(2);   // 兩個願望兩組頁

        String sheet = sheetOf(zip.get(dir + "02_捐物人資料.xlsx"));
        assertThat(sheet).doesNotContain("123321").contains("OSK-000001", "OSK-000002", "王大明");
        assertThat(sheet).contains("t=\"inlineStr\"><is><t xml:space=\"preserve\">" + rocToday() + "</t>");
        assertThat(sheet).contains("<dimension ref=\"A1:G4\"/>");
        assertThat(pages(zip.get(dir + "02_捐物人資料.pdf"))).isEqualTo(1);
        assertThat(pages(zip.get("03_收據/紅聯（給捐贈人）/OSK-000001.pdf"))).isEqualTo(1);
        assertThat(pages(zip.get("03_收據/白黃聯（協會留存）/OSK-000001.pdf"))).isEqualTo(2);

        String todo = new String(zip.get("05_留存備份/待補資料.md"), StandardCharsets.UTF_8);
        assertThat(todo).contains("| 缺什麼 |");
        // 證號、身分資料不會出現在任何文字檔與 Excel 裡
        for (Map.Entry<String, byte[]> e : zip.entrySet()) {
            if (!e.getKey().endsWith(".pdf")) {
                assertThat(new String(e.getValue(), StandardCharsets.UTF_8)).as(e.getKey()).doesNotContain("A123456789");
            }
        }
    }

    @Test
    @DisplayName("D 結案備查：支出明細 Excel 與核章 PDF（每張收據一列，傳票號碼先用收據編號）")
    void stageD() throws Exception {
        completedClaim("第一個");

        Map<String, byte[]> zip = buildPackage(ClosingStage.D);

        String dir = "02_交給康乃心/D.結案備查/";
        assertThat(zip.keySet()).contains(dir + "04_支出明細資料.xlsx", dir + "04_支出明細資料.pdf",
                dir + "03_執行(服務)成果報告.pdf", "05_留存備份/結案登記簿.xlsx");
        assertThat(zip.keySet()).noneMatch(n -> n.contains("捐物人資料"));
        assertThat(pages(zip.get(dir + "03_執行(服務)成果報告.pdf"))).isGreaterThanOrEqualTo(1);
        // 登記簿：五個工作表，含受贈與轉贈、收據、現金與自籌、勾稽、來源索引
        String workbook = new String(unzipEntry(zip.get("05_留存備份/結案登記簿.xlsx"), "xl/workbook.xml"), StandardCharsets.UTF_8);
        assertThat(workbook).contains("受贈與轉贈", "收據", "現金與自籌", "勾稽", "來源索引");
        String sheet = sheetOf(zip.get(dir + "04_支出明細資料.xlsx"));
        assertThat(sheet).doesNotContain("123321").contains("財物支出", "OSK-000001", "其他", "實物轉貨".replace("貨", "贈"))
                .contains("轉贈快樂兒童之家");
        assertThat(sheet).contains("<dimension ref=\"A1:H2\"/>");
    }

    @Test
    @DisplayName("併報：捐物人資料（02）與支出明細（06）都有，在「成果結案備查併報」資料夾")
    void stageCombined() throws Exception {
        completedClaim("第一個");

        Map<String, byte[]> zip = buildPackage(ClosingStage.CD);

        String dir = "02_交給康乃心/成果結案備查併報/";
        assertThat(zip.keySet()).contains(dir + "02_捐物人資料.xlsx", dir + "02_捐物人資料.pdf",
                dir + "06_支出明細資料.xlsx", dir + "06_支出明細資料.pdf");
    }

    @Test
    @DisplayName("收到日在截止日之後的收據不收進本階段文件，列在待補資料")
    void receiptsAfterTheCutoffAreListed() throws Exception {
        UUID claim = completedClaim("太晚收到");
        UUID receiptId = receipts.findValidByClaimId(claim).orElseThrow().getId();
        entityManager.flush();
        jdbc.update("UPDATE donation_receipts SET donated_on = ? WHERE id = ?",
                java.sql.Date.valueOf(campaign.getCollectionEnd().plusDays(3)), receiptId);
        entityManager.clear();
        completedClaim("準時");

        Map<String, byte[]> zip = buildPackage(ClosingStage.C);

        String sheet = sheetOf(zip.get("02_交給康乃心/C.成果備查/02_捐物人資料.xlsx"));
        assertThat(sheet).contains("OSK-000002").doesNotContain("OSK-000001");
        assertThat(new String(zip.get("05_留存備份/待補資料.md"), StandardCharsets.UTF_8))
                .contains("收到日不在本階段資料範圍").contains("OSK-000001");
    }

    @Test
    @DisplayName("沒有收據不能建立；只有管理員能建立")
    void guards() throws Exception {
        mvc.perform(as(post("/api/admin/campaigns/{id}/closing-packages", campaign.getId()).param("stage", "C"), ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("NO_RECEIPTS"));
        mvc.perform(as(post("/api/admin/campaigns/{id}/closing-packages", campaign.getId()).param("stage", "C"), DONOR))
                .andExpect(status().isForbidden());
    }

    private static String rocToday() {
        return "%03d/%02d/%02d".formatted(TODAY.getYear() - 1911, TODAY.getMonthValue(), TODAY.getDayOfMonth());
    }
}
