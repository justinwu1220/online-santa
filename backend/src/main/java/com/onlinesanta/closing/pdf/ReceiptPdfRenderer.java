package com.onlinesanta.closing.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.springframework.stereotype.Component;

/**
 * 產生收據三聯單：在範本上疊上這張收據的內容，再拆成白聯與黃紅聯兩個檔案。
 *
 * <p>範本（{@link ReceiptTemplate}）已含全部固定文字；這裡只疊上每張收據自己的內容，
 * 用字形輪廓繪製（見 {@link GlyphOutlines}）。三聯內容完全一致，只有底色與聯別不同（範本本身決定）。拆檔照使用者指定：
 * 白聯一頁一檔；黃聯、紅聯兩頁一檔（黃在前、紅在後）。
 *
 * <p>版面座標都集中在 {@link Layout}，單位 pt、PDF 座標（原點左下、y 向上），{@code y} 一律是文字基線。
 * 數值取自範本文字的實際位置與 SPEC 的定位提示，之後調整版面只動這一處。
 */
@Component
public class ReceiptPdfRenderer {

    /** 拆好的兩個檔案。 */
    public record Rendered(byte[] white, byte[] yellowRed) {
    }

    /** 版面常數。 */
    static final class Layout {
        static final float[] BLACK = {0f, 0f, 0f};
        static final float[] RED = {0.784f, 0.0627f, 0.1804f};

        // 開立日期（範本「中華民國　年　月　日」那行的空格處），基線同該行文字
        static final float ISSUE_Y = 378.4f;
        static final float ISSUE_YEAR_CX = 243f;
        static final float ISSUE_MONTH_CX = 284.5f;
        static final float ISSUE_DAY_CX = 326.5f;

        // 右上角收據編號：右對齊，原編號文字的基線
        static final float NUMBER_RIGHT_X = 566.9f;
        static final float NUMBER_Y = 377.0f;

        // 捐贈人與身分證字號：寫在預印底線上方
        static final float DONOR_X = 82f;
        static final float DONOR_Y = 351.2f;
        static final float DONOR_WIDTH = 182f;
        static final float ID_X = 143f;
        static final float ID_Y = 330.4f;
        static final float ID_WIDTH = 108f;

        // 捐贈日期（範本「捐贈日期：中華民國　年　月　日」）
        static final float DONATE_Y = 328.8f;
        static final float DONATE_YEAR_CX = 372f;
        static final float DONATE_MONTH_CX = 410f;
        static final float DONATE_DAY_CX = 448f;

        // 勸募活動／許可文號、許可期間／捐贈用途兩行（取代範本預印的兩行）
        static final float ACTIVITY_X = 39.7f;
        static final float ACTIVITY_Y = 310.4f;
        static final float PERIOD_Y = 295.7f;
        static final float INFO_WIDTH = 440f;
        static final float INFO_SIZE = 8f;

        // 物資明細列（單列，範本欄線：名稱 101.6–252.7、數量 253–303.7、時價 303.7–387.7、備註 388–449）
        static final float ITEM_X = 105f;
        static final float ITEM_WIDTH = 144f;
        static final float ROW_BASELINE = 237.0f;
        static final float ROW_CENTER = 242.2f;
        static final float QTY_CX = 278f;
        static final float VALUE_CX = 345.7f;

        // 大寫金額：七格，填在預印單位字（佰拾萬仟佰拾元）的左邊
        static final float TOTAL_Y = 201.5f;
        static final float[] TOTAL_CX = {145f, 185f, 225f, 265f, 304.5f, 344.5f, 415f};

        // 實際收件機構：預印底線上方
        static final float RECIPIENT_X = 108f;
        static final float RECIPIENT_Y = 172.6f;
        static final float RECIPIENT_WIDTH = 182f;

        static final float DEFAULT_SIZE = 10.5f;
        /** 中文不小於 9pt（SPEC）。 */
        static final float MIN_SIZE = 9f;
        static final float LINE_HEIGHT = 11.5f;
        static final int MAX_ITEM_LINES = 3;

        private Layout() {
        }
    }

    private final ReceiptTemplate template;
    private final GlyphOutlines glyphs;

    public ReceiptPdfRenderer(ReceiptTemplate template, GlyphOutlines glyphs) {
        this.template = template;
        this.glyphs = glyphs;
    }

    /**
     * @throws ReceiptLayoutException 內容放不進版面（字太長、字型缺字、金額超出七位數）
     */
    public Rendered render(ReceiptData data) {
        try (PDDocument doc = Loader.loadPDF(template.pdf())) {
            // 每個字形在這份文件裡只定義一次，三頁共用（見 GlyphOutlines.Session）
            GlyphOutlines.Session g = glyphs.session(doc);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                PDPage page = doc.getPage(i);
                try (PDPageContentStream cs = new PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    drawContent(g, cs, data);
                }
            }
            return new Rendered(save(doc, 0, 1), save(doc, 1, 3));
        } catch (IOException e) {
            throw new UncheckedIOException("產生收據 PDF 失敗：" + data.receiptNo(), e);
        }
    }

    private void drawContent(GlyphOutlines.Session g, PDPageContentStream cs, ReceiptData d) throws IOException {
        // 編號（紅字、右對齊）
        cs.setNonStrokingColor(Layout.RED[0], Layout.RED[1], Layout.RED[2]);
        fitted(g, cs, "收據編號", d.receiptNo(), Layout.NUMBER_RIGHT_X, Layout.NUMBER_Y, 90f, 12f, 10f, Align.RIGHT);

        cs.setNonStrokingColor(Layout.BLACK[0], Layout.BLACK[1], Layout.BLACK[2]);

        // 開立日期
        rocDate(g, cs, "開立日期", d.issuedOn(), Layout.ISSUE_Y, Layout.ISSUE_YEAR_CX, Layout.ISSUE_MONTH_CX,
                Layout.ISSUE_DAY_CX, 10.5f);

        fitted(g, cs, "捐贈人", d.donorName(), Layout.DONOR_X, Layout.DONOR_Y, Layout.DONOR_WIDTH,
                Layout.DEFAULT_SIZE, Layout.MIN_SIZE, Align.LEFT);
        if (d.donorIdNumber() != null && !d.donorIdNumber().isBlank()) {
            fitted(g, cs, "身分證字號／統一編號", d.donorIdNumber(), Layout.ID_X, Layout.ID_Y, Layout.ID_WIDTH,
                    Layout.DEFAULT_SIZE, Layout.MIN_SIZE, Align.LEFT);
        }
        rocDate(g, cs, "捐贈日期", d.donatedOn(), Layout.DONATE_Y, Layout.DONATE_YEAR_CX, Layout.DONATE_MONTH_CX,
                Layout.DONATE_DAY_CX, 9.5f);

        // 勸募活動、許可文號與期間（一行放不下就視為版面問題，不縮到 8pt 以下）
        String activityLine = "勸募活動：" + d.activityName() + "　　　勸募許可文號：" + d.permitNo();
        fitted(g, cs, "勸募活動與許可文號", activityLine, Layout.ACTIVITY_X, Layout.ACTIVITY_Y, Layout.INFO_WIDTH,
                Layout.INFO_SIZE, Layout.INFO_SIZE, Align.LEFT);
        String periodLine = "許可勸募期間：中華民國" + rocFull(d.collectionStart()) + "至" + rocFull(d.collectionEnd())
                + "　　　捐贈用途：" + d.purpose();
        fitted(g, cs, "許可勸募期間與捐贈用途", periodLine, Layout.ACTIVITY_X, Layout.PERIOD_Y, Layout.INFO_WIDTH,
                Layout.INFO_SIZE, Layout.INFO_SIZE, Align.LEFT);

        drawItem(g, cs, d);

        // 大寫金額
        String[] digits = ChineseNumerals.slots(checkAmount(d.valueYuan()));
        for (int i = 0; i < digits.length; i++) {
            fitted(g, cs, "大寫金額", digits[i], Layout.TOTAL_CX[i], Layout.TOTAL_Y, 14f, Layout.DEFAULT_SIZE,
                    Layout.DEFAULT_SIZE, Align.CENTER);
        }

        fitted(g, cs, "實際收件機構", d.recipientOrgName(), Layout.RECIPIENT_X, Layout.RECIPIENT_Y,
                Layout.RECIPIENT_WIDTH, Layout.DEFAULT_SIZE, Layout.MIN_SIZE, Align.LEFT);
    }

    private static int checkAmount(int amount) {
        if (amount < 0 || amount > 9_999_999) {
            throw new ReceiptLayoutException("物資時價", "金額 " + amount + " 元放不進收據的大寫欄（上限 9,999,999 元）");
        }
        return amount;
    }

    /** 物資名稱：單行優先，放不下就縮到 9pt，再不行換行（最多三行），仍放不下就丟例外。 */
    private void drawItem(GlyphOutlines.Session g, PDPageContentStream cs, ReceiptData d) throws IOException {
        String name = d.itemDescription();
        checkGlyphs("物資名稱與規格", name);
        float size = Layout.DEFAULT_SIZE;
        List<String> lines = null;
        for (float s = size; s >= Layout.MIN_SIZE; s -= 0.5f) {
            if (glyphs.width(name, s) <= Layout.ITEM_WIDTH) {
                size = s;
                lines = List.of(name);
                break;
            }
        }
        if (lines == null) {
            size = Layout.MIN_SIZE;
            lines = wrap(name, Layout.ITEM_WIDTH, size);
            if (lines.size() > Layout.MAX_ITEM_LINES) {
                throw new ReceiptLayoutException("物資名稱與規格",
                        "內容太長，三行仍放不下（需要 " + lines.size() + " 行）");
            }
        }
        float firstBaseline = lines.size() == 1
                ? Layout.ROW_BASELINE
                : Layout.ROW_CENTER + (lines.size() - 1) * Layout.LINE_HEIGHT / 2f - size * 0.35f;
        for (int i = 0; i < lines.size(); i++) {
            g.draw(cs, lines.get(i), Layout.ITEM_X, firstBaseline - i * Layout.LINE_HEIGHT, size);
        }

        fitted(g, cs, "數量", d.quantity() + " " + d.unit(), Layout.QTY_CX, Layout.ROW_BASELINE, 46f,
                Layout.DEFAULT_SIZE, Layout.MIN_SIZE, Align.CENTER);
        fitted(g, cs, "物資時價", NumberFormat.getIntegerInstance(Locale.TAIWAN).format(d.valueYuan()),
                Layout.VALUE_CX, Layout.ROW_BASELINE, 78f, Layout.DEFAULT_SIZE, Layout.MIN_SIZE, Align.CENTER);
    }

    private List<String> wrap(String text, float width, float size) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            String ch = new String(Character.toChars(cp));
            if (line.length() > 0 && glyphs.width(line + ch, size) > width) {
                lines.add(line.toString());
                line = new StringBuilder();
            }
            line.append(ch);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    private void rocDate(GlyphOutlines.Session g, PDPageContentStream cs, String field, LocalDate date, float y, float yearCx,
                         float monthCx, float dayCx, float size) throws IOException {
        fitted(g, cs, field, String.valueOf(date.getYear() - 1911), yearCx, y, 26f, size, size, Align.CENTER);
        fitted(g, cs, field, String.valueOf(date.getMonthValue()), monthCx, y, 26f, size, size, Align.CENTER);
        fitted(g, cs, field, String.valueOf(date.getDayOfMonth()), dayCx, y, 26f, size, size, Align.CENTER);
    }

    private static String rocFull(LocalDate date) {
        return (date.getYear() - 1911) + "年" + date.getMonthValue() + "月" + date.getDayOfMonth() + "日";
    }

    private enum Align { LEFT, CENTER, RIGHT }

    private void fitted(GlyphOutlines.Session g, PDPageContentStream cs, String field, String text, float x, float baseline, float maxWidth,
                        float size, float minSize, Align align) throws IOException {
        checkGlyphs(field, text);
        float use = size;
        while (glyphs.width(text, use) > maxWidth) {
            use -= 0.5f;
            if (use < minSize) {
                throw new ReceiptLayoutException(field, "內容太長，放不進欄位（最小 " + minSize + "pt）：" + text);
            }
        }
        float width = glyphs.width(text, use);
        float left = switch (align) {
            case LEFT -> x;
            case CENTER -> x - width / 2f;
            case RIGHT -> x - width;
        };
        g.draw(cs, text, left, baseline, use);
    }

    private void checkGlyphs(String field, String text) {
        String missing = glyphs.missingChars(text);
        if (!missing.isEmpty()) {
            throw new ReceiptLayoutException(field, "字型沒有這些字，無法印出：" + missing);
        }
    }

    /** 取出 [from, to) 頁成為獨立 PDF。 */
    private static byte[] save(PDDocument source, int from, int to) throws IOException {
        try (PDDocument part = new PDDocument()) {
            for (int i = from; i < to; i++) {
                part.importPage(source.getPage(i));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            part.save(out);
            return out.toByteArray();
        }
    }
}
