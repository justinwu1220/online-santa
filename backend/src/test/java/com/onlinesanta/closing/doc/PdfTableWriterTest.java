package com.onlinesanta.closing.doc;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.onlinesanta.closing.doc.PdfTableWriter.Align;
import com.onlinesanta.closing.doc.PdfTableWriter.Column;
import com.onlinesanta.closing.pdf.GlyphOutlines;

@DisplayName("核章用清冊 PDF（A4 橫式）")
class PdfTableWriterTest {

    private static final PdfTableWriter WRITER = new PdfTableWriter(new GlyphOutlines());

    private static PdfTableWriter.Spec spec(int rows, String donorName) {
        List<List<String>> data = new ArrayList<>();
        for (int i = 1; i <= rows; i++) {
            data.add(List.of("OS2026-%06d".formatted(i), "115/12/25", i % 7 == 0 ? donorName : "王小明",
                    "兒童益智積木玩具組（含說明書與收納袋，適合四歲以上）（單位：份）", "1", "1,200",
                    "本活動服務對象之個人聖誕禮物"));
        }
        return new PdfTableWriter.Spec("捐物人資料（捐贈清冊）",
                List.of("勸募團體全銜：社團法人彰化縣康乃心關懷協會",
                        "活動名稱：線上聖誕老人2026聖誕禮物勸募活動　　活動編號：115000877",
                        "勸募許可文號：衛部救字第1150000號",
                        "勸募期間：中華民國115年11月25日至中華民國116年1月25日",
                        "資料截止日：中華民國116年1月25日　　共 " + rows + " 筆"),
                List.of(new Column("收據編號", 1.5f, Align.LEFT), new Column("捐贈日期\n(YYY/MM/dd)", 1.2f, Align.CENTER),
                        new Column("捐贈者名稱或姓名", 1.7f, Align.LEFT), new Column("捐贈物資名稱", 3.1f, Align.LEFT),
                        new Column("數量", 0.7f, Align.RIGHT), new Column("時價（元）", 1.0f, Align.RIGHT),
                        new Column("捐贈用途", 2.2f, Align.LEFT)),
                data, List.of("合計", "", "", "共 %d 筆".formatted(rows), String.valueOf(rows), "%,d".formatted(rows * 1200L), ""),
                "線上聖誕老人 2026　成果備查　資料截止日 116/01/25");
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

    private static int pagesOf(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return doc.getNumberOfPages();
        }
    }

    @Test
    @DisplayName("資料少：一頁，簽章四格在同一頁")
    void smallListFitsOnOnePage() throws Exception {
        PdfTableWriter.Result result = WRITER.render(spec(5, "王大明"));

        assertThat(result.pages()).isEqualTo(1);
        assertThat(pagesOf(result.pdf())).isEqualTo(1);
        try (PDDocument doc = Loader.loadPDF(result.pdf())) {
            assertThat(doc.getPage(0).getMediaBox().getWidth()).isGreaterThan(doc.getPage(0).getMediaBox().getHeight());
        }
        assertThat(result.replacedChars()).isEmpty();
        preview("donation-small", result.pdf());
    }

    @Test
    @DisplayName("資料多：多頁，每頁重複抬頭與欄名，簽章在最後；檔案大小合理")
    void longListSpansPages() throws Exception {
        PdfTableWriter.Result result = WRITER.render(spec(120, "王大明"));

        assertThat(result.pages()).isGreaterThan(2);
        assertThat(pagesOf(result.pdf())).isEqualTo(result.pages());
        assertThat(result.pdf().length).isLessThan(1_500_000);
        preview("donation-long", result.pdf());
    }

    @Test
    @DisplayName("字型沒有的字畫成「？」並回報，不丟例外")
    void missingGlyphsAreReplacedAndReported() throws Exception {
        PdfTableWriter.Result result = WRITER.render(spec(7, "𠮷野家"));

        assertThat(result.replacedChars()).contains("𠮷");
        assertThat(pagesOf(result.pdf())).isEqualTo(1);
        preview("donation-missing-glyph", result.pdf());
    }

    @Test
    @DisplayName("沒有任何資料：仍有抬頭、合計列與簽章")
    void emptyList() throws Exception {
        PdfTableWriter.Result result = WRITER.render(spec(0, "王大明"));

        assertThat(result.pages()).isEqualTo(1);
    }
}
