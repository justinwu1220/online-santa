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
import com.onlinesanta.closing.doc.ClosingDataset.FinanceSummary;
import com.onlinesanta.closing.doc.ClosingDataset.GiftRow;
import com.onlinesanta.closing.doc.ClosingDataset.OrgGroup;
import com.onlinesanta.closing.doc.ClosingDataset.Totals;
import com.onlinesanta.closing.pdf.GlyphOutlines;

@DisplayName("執行（服務）成果報告 PDF")
class ReportPdfWriterTest {

    private static final ReportPdfWriter WRITER = new ReportPdfWriter(new GlyphOutlines(),
            new ClosingProperties(30, "本活動服務對象之個人聖誕禮物", "社團法人彰化縣康乃心關懷協會"));

    private static ClosingDataset dataset(ClosingStage stage, int orgCount) {
        Campaign c = new Campaign("線上聖誕老人 2026", LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25),
                LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25), LocalDate.of(2026, 11, 25),
                LocalDate.of(2027, 1, 20), "OS2026", 1);
        c.update(c.getName(), "115000877", "衛部救字第1150000號", "線上聖誕老人2026聖誕禮物勸募活動", c.getCollectionStart(),
                c.getCollectionEnd(), c.getUseStart(), c.getUseEnd(), c.getWishListStart(), c.getWishListEnd(), "OS2026",
                null, null, CampaignStatus.ACTIVE);
        List<OrgGroup> orgs = new ArrayList<>();
        int qty = 0;
        long value = 0;
        for (int i = 1; i <= orgCount; i++) {
            UUID id = UUID.randomUUID();
            GiftRow g = new GiftRow(UUID.randomUUID(), "OS2026-%06d".formatted(i), LocalDate.of(2026, 12, 25), "王小明",
                    "積木", 2, "份", 1200, id, "財團法人快樂兒童之家第" + i + "分院", "小星", "積木");
            orgs.add(new OrgGroup(id, "財團法人快樂兒童之家第" + i + "分院", "12345678", null, null, null, null, null, false,
                    List.of(g), 2, 1200));
            qty += 2;
            value += 1200;
        }
        return new ClosingDataset(c, stage, c.getCollectionStart(), stage.cutoff(c), List.of(), orgs, List.of(),
                new Totals(orgCount, qty, value, orgCount), new FinanceSummary(null, null, null, null, null, List.of()),
                List.of(), List.of());
    }

    private static void preview(String name, byte[] pdf) throws IOException {
        Path dir = Path.of("target", "closing-preview");
        Files.createDirectories(dir);
        Files.write(dir.resolve(name + ".pdf"), pdf);
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 80);
                ImageIO.write(image, "png", dir.resolve("%s-%d.png".formatted(name, i + 1)).toFile());
            }
        }
    }

    @Test
    @DisplayName("D 成果報告：A4 直式，各段落齊全，簽章在最後")
    void stageDReport() throws Exception {
        ReportPdfWriter.Result result = WRITER.write(dataset(ClosingStage.D, 3));

        assertThat(result.pages()).isGreaterThanOrEqualTo(1);
        assertThat(result.replacedChars()).isEmpty();
        try (PDDocument doc = Loader.loadPDF(result.pdf())) {
            assertThat(doc.getPage(0).getMediaBox().getHeight()).isGreaterThan(doc.getPage(0).getMediaBox().getWidth());
        }
        preview("report-d", result.pdf());
    }

    @Test
    @DisplayName("機構很多時自動換頁，表格欄名每頁重畫")
    void manyOrganizationsSpanPages() throws Exception {
        ReportPdfWriter.Result result = WRITER.write(dataset(ClosingStage.CD, 60));

        assertThat(result.pages()).isGreaterThan(1);
        preview("report-cd-long", result.pdf());
    }
}
