package com.onlinesanta.closing.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 收據 PDF 引擎：去字範本、重畫文字、拆聯、版面溢出。 */
class ReceiptPdfRendererTest {

    private static final GlyphOutlines GLYPHS = new GlyphOutlines();
    private static final ReceiptTemplate TEMPLATE = new ReceiptTemplate();
    private static final ReceiptPdfRenderer RENDERER = new ReceiptPdfRenderer(TEMPLATE, GLYPHS);

    private static ReceiptData sample() {
        return new ReceiptData("OS2026-000042", LocalDate.of(2026, 12, 28), "王小明", "A123456789",
                LocalDate.of(2026, 12, 25), "線上聖誕老人2026聖誕禮物勸募活動",
                "衛部救字第1150000號", LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25),
                "本活動服務對象之個人聖誕禮物", "積木玩具組（含說明書）", 1, "份", 1200, "彰化縣快樂兒童之家");
    }

    private static String textOf(byte[] pdf) throws Exception {
        try (PDDocument d = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(d);
        }
    }

    private static int pagesOf(byte[] pdf) throws Exception {
        try (PDDocument d = Loader.loadPDF(pdf)) {
            return d.getNumberOfPages();
        }
    }

    /** 預覽圖放在 target/receipt-preview，方便人工逐張目視檢查。 */
    private static void preview(String name, byte[] pdf, int page) throws Exception {
        Path dir = Path.of("target", "receipt-preview");
        Files.createDirectories(dir);
        Files.write(dir.resolve(name + ".pdf"), pdf);
        try (PDDocument d = Loader.loadPDF(pdf)) {
            ImageIO.write(new PDFRenderer(d).renderImageWithDPI(page, 130), "png", new File(dir.toFile(), name + ".png"));
        }
    }

    @Test
    @DisplayName("白聯 1 頁、黃紅聯 2 頁；範本預印的舊編號、待確認、舊許可行沒有任何殘留")
    void splitsIntoWhiteAndYellowRedWithoutResidualText() throws Exception {
        ReceiptPdfRenderer.Rendered r = RENDERER.render(sample());

        assertThat(pagesOf(r.white())).isEqualTo(1);
        assertThat(pagesOf(r.yellowRed())).isEqualTo(2);
        // 固定標籤是真文字（可擷取）；範本預印的填寫位置文字必須已移除，內容以輪廓繪製（不可擷取）
        for (byte[] pdf : new byte[][] {r.white(), r.yellowRed()}) {
            String text = textOf(pdf);
            assertThat(text).contains("捐贈收據").contains("實際收件機構").contains("合計新臺幣");
            assertThat(text).doesNotContain("OS2026-000001").doesNotContain("待確認")
                    .doesNotContain("許可勸募期間").doesNotContain("勸募活動：");
        }

        preview("white", r.white(), 0);
        preview("yellow", r.yellowRed(), 0);
        preview("red", r.yellowRed(), 1);
    }

    @Test
    @DisplayName("單份檔案大小在合理範圍（Logo 已壓縮）")
    void fileSizeIsReasonable() {
        ReceiptPdfRenderer.Rendered r = RENDERER.render(sample());
        assertThat(r.white().length).isLessThan(300_000);
        assertThat(r.yellowRed().length).isLessThan(300_000);
    }

    @Test
    @DisplayName("沒有證號（未勾選要收據）也能產生，不填假證號")
    void worksWithoutIdNumber() throws Exception {
        ReceiptData d = sample();
        ReceiptPdfRenderer.Rendered r = RENDERER.render(new ReceiptData(d.receiptNo(), d.issuedOn(), "善心人士", null,
                d.donatedOn(), d.activityName(), d.permitNo(), d.collectionStart(), d.collectionEnd(), d.purpose(),
                d.itemDescription(), 2, "份", 0, d.recipientOrgName()));
        assertThat(pagesOf(r.white())).isEqualTo(1);
        preview("no-id-zero-value", r.white(), 0);
    }

    @Test
    @DisplayName("長姓名、長品名會縮字或換行，仍放進版面")
    void longTextShrinksOrWraps() throws Exception {
        ReceiptData d = sample();
        ReceiptPdfRenderer.Rendered r = RENDERER.render(new ReceiptData(d.receiptNo(), d.issuedOn(),
                "財團法人台灣兒童與青少年關懷慈善基金會", "12345678", d.donatedOn(), d.activityName(), d.permitNo(),
                d.collectionStart(), d.collectionEnd(), d.purpose(),
                "兒童益智積木玩具組（含說明書與收納袋，適合四歲以上）", 12, "份", 9_999_999, "財團法人彰化縣身心障礙者福利服務中心"));
        assertThat(pagesOf(r.white())).isEqualTo(1);
        preview("long-text", r.white(), 0);
    }

    @Test
    @DisplayName("放不下就丟例外並指出欄位：不裁字、不漏列")
    void overflowFailsLoudly() {
        ReceiptData d = sample();
        assertThatThrownBy(() -> RENDERER.render(new ReceiptData(d.receiptNo(), d.issuedOn(),
                "王".repeat(60), null, d.donatedOn(), d.activityName(), d.permitNo(), d.collectionStart(),
                d.collectionEnd(), d.purpose(), d.itemDescription(), 1, "份", 100, d.recipientOrgName())))
                .isInstanceOf(ReceiptLayoutException.class).hasMessageContaining("捐贈人");

        assertThatThrownBy(() -> RENDERER.render(new ReceiptData(d.receiptNo(), d.issuedOn(), d.donorName(), null,
                d.donatedOn(), d.activityName(), d.permitNo(), d.collectionStart(), d.collectionEnd(), d.purpose(),
                "物".repeat(120), 1, "份", 100, d.recipientOrgName())))
                .isInstanceOf(ReceiptLayoutException.class).hasMessageContaining("物資名稱");

        assertThatThrownBy(() -> RENDERER.render(new ReceiptData(d.receiptNo(), d.issuedOn(), d.donorName(), null,
                d.donatedOn(), d.activityName(), d.permitNo(), d.collectionStart(), d.collectionEnd(), d.purpose(),
                d.itemDescription(), 1, "份", 10_000_000, d.recipientOrgName())))
                .isInstanceOf(ReceiptLayoutException.class).hasMessageContaining("物資時價");
    }

    @Test
    @DisplayName("字型沒有的字會指名報錯，不會印出空白或亂碼")
    void missingGlyphIsReported() {
        ReceiptData d = sample();
        // U+10400 是字型不收錄的字元
        String rare = new String(Character.toChars(0x10400));
        assertThatThrownBy(() -> RENDERER.render(new ReceiptData(d.receiptNo(), d.issuedOn(), "王" + rare, null,
                d.donatedOn(), d.activityName(), d.permitNo(), d.collectionStart(), d.collectionEnd(), d.purpose(),
                d.itemDescription(), 1, "份", 100, d.recipientOrgName())))
                .isInstanceOf(ReceiptLayoutException.class).hasMessageContaining("字型沒有");
    }

    @Test
    @DisplayName("中文大寫金額：七格、高位補零；完整讀法")
    void chineseNumerals() {
        assertThat(ChineseNumerals.slots(1200)).containsExactly("零", "零", "零", "壹", "貳", "零", "零");
        assertThat(ChineseNumerals.slots(9_999_999)).containsOnly("玖");
        assertThat(ChineseNumerals.slots(0)).containsOnly("零");
        assertThat(ChineseNumerals.spoken(1200)).isEqualTo("壹仟貳佰元整");
        assertThat(ChineseNumerals.spoken(10_001)).isEqualTo("壹萬零壹元整");
        assertThat(ChineseNumerals.spoken(100_000)).isEqualTo("壹拾萬元整");
        assertThat(ChineseNumerals.spoken(0)).isEqualTo("零元整");
        assertThatThrownBy(() -> ChineseNumerals.slots(10_000_000)).isInstanceOf(IllegalArgumentException.class);
    }
}
