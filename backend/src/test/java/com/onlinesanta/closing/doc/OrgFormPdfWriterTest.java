package com.onlinesanta.closing.doc;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.onlinesanta.closing.Campaign;
import com.onlinesanta.closing.CampaignStatus;
import com.onlinesanta.closing.ClosingProperties;
import com.onlinesanta.closing.doc.ClosingDataset.GiftRow;
import com.onlinesanta.closing.doc.ClosingDataset.OrgGroup;
import com.onlinesanta.closing.doc.ClosingDataset.Totals;
import com.onlinesanta.closing.doc.OrgFormPdfWriter.Form;
import com.onlinesanta.closing.pdf.GlyphOutlines;

@DisplayName("05A／05B／05C 機構確認表 PDF")
class OrgFormPdfWriterTest {

    private static final OrgFormPdfWriter WRITER = new OrgFormPdfWriter(new GlyphOutlines(),
            new ClosingProperties(30, "本活動服務對象之個人聖誕禮物", "社團法人彰化縣康乃心關懷協會"));

    private static OrgGroup org(UUID id, String name, boolean association, int gifts) {
        List<GiftRow> rows = new ArrayList<>();
        for (int i = 1; i <= gifts; i++) {
            rows.add(new GiftRow(UUID.randomUUID(), "OS2026-%06d".formatted(i), LocalDate.of(2026, 12, 25), "王小明",
                    "兒童益智積木玩具組（含說明書）", 1, "份", 1200, id, name, "小星" + i, "積木" + i));
        }
        return new OrgGroup(id, name, "12345678", "彰化縣花壇鄉文德村宏宅街12巷12號", "李主任", "王承辦", "04-7866473",
                "a@example.org", association, rows, gifts, gifts * 1200L);
    }

    private static ClosingDataset dataset(UUID associationId, List<OrgGroup> orgs) {
        Campaign c = new Campaign("線上聖誕老人 2026", LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25),
                LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25), LocalDate.of(2026, 11, 25),
                LocalDate.of(2027, 1, 20), "OS2026", 1);
        c.update(c.getName(), "115000877", "衛部救字第1150000號", "線上聖誕老人2026聖誕禮物勸募活動", c.getCollectionStart(),
                c.getCollectionEnd(), c.getUseStart(), c.getUseEnd(), c.getWishListStart(), c.getWishListEnd(), "OS2026",
                null, associationId, CampaignStatus.ACTIVE);
        return new ClosingDataset(c, ClosingStage.C, c.getCollectionStart(), c.getCollectionEnd(), List.of(), orgs, List.of(),
                new Totals(0, 0, 0, orgs.size()), List.of(), List.of());
    }

    private static int pages(byte[] pdf) throws IOException {
        try (PDDocument d = Loader.loadPDF(pdf)) {
            return d.getNumberOfPages();
        }
    }

    private static void preview(String name, byte[] pdf, int maxPages) throws IOException {
        Path dir = Path.of("target", "closing-preview");
        Files.createDirectories(dir);
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            for (int i = 0; i < Math.min(maxPages, doc.getNumberOfPages()); i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 80);
                ImageIO.write(image, "png", dir.resolve("%s-%d.png".formatted(name, i + 1)).toFile());
            }
        }
    }

    @Test
    @DisplayName("每個外部機構一份 05A 與一份 05B，協會自身服務對象一份 05C；檔名不含個人資料")
    void oneOfEachPerOrganization() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID assoc = UUID.randomUUID();
        List<Form> forms = WRITER.build(dataset(assoc, List.of(org(a, "財團法人快樂兒童之家", false, 3),
                org(b, "光明/啟智 學校", false, 1), org(assoc, "康乃心服務對象", true, 2))));

        // 外部機構依傳入（資料服務已排序）的順序編號；路徑只留機構簡稱，不含個人資料
        assertThat(forms).extracting(Form::path).containsExactly(
                "04_機構確認/待簽章/001_財團法人快樂兒童之家/05A_機構物資領受暨發放確認表.pdf",
                "04_機構確認/待簽章/001_財團法人快樂兒童之家/05B_個人物資領受證明_機構留存.pdf",
                "04_機構確認/待簽章/002_光明啟智學校/05A_機構物資領受暨發放確認表.pdf",
                "04_機構確認/待簽章/002_光明啟智學校/05B_個人物資領受證明_機構留存.pdf",
                "04_機構確認/康乃心自身服務對象/05C_康乃心服務對象物資領受確認表.pdf");
        for (Form f : forms) {
            assertThat(f.replacedChars()).as(f.path()).isEmpty();
        }
    }

    @Test
    @DisplayName("05A 橫式一份；05B 與 05C 每個受贈人（願望）一組頁、各自獨立簽章頁")
    void pageLayout() throws Exception {
        UUID a = UUID.randomUUID();
        UUID assoc = UUID.randomUUID();
        List<Form> forms = WRITER.build(dataset(assoc, List.of(org(a, "快樂之家", false, 3), org(assoc, "康乃心服務對象", true, 2))));

        Form f05a = forms.get(0);
        Form f05b = forms.get(1);
        Form f05c = forms.get(2);
        try (PDDocument doc = Loader.loadPDF(f05a.pdf())) {
            assertThat(doc.getPage(0).getMediaBox().getWidth()).isGreaterThan(doc.getPage(0).getMediaBox().getHeight());
        }
        assertThat(pages(f05a.pdf())).isEqualTo(1);
        assertThat(pages(f05b.pdf())).isEqualTo(3);          // 三個願望三組頁
        assertThat(pages(f05c.pdf())).isEqualTo(2);
        preview("form-05A", f05a.pdf(), 1);
        preview("form-05B", f05b.pdf(), 1);
        preview("form-05C", f05c.pdf(), 1);
    }

    @Test
    @DisplayName("沒有協會自身服務對象就不產 05C；機構資料缺漏時寫待確認")
    void missingDataIsPending() throws Exception {
        UUID a = UUID.randomUUID();
        OrgGroup bare = new OrgGroup(a, "資料不全機構", null, null, null, null, null, null, false,
                List.of(new GiftRow(UUID.randomUUID(), "OS2026-000001", LocalDate.of(2026, 12, 25), "王小明", "積木", 1, "份", 1200,
                        a, "資料不全機構", null, null)), 1, 1200);

        List<Form> forms = WRITER.build(dataset(null, List.of(bare)));

        assertThat(forms).hasSize(2);
        assertThat(forms).noneMatch(f -> f.path().contains("05C"));
        preview("form-05A-missing", forms.get(0).pdf(), 1);
    }
}
