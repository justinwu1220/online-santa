package com.onlinesanta.closing.doc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import com.onlinesanta.closing.pdf.GlyphOutlines;

/**
 * 核章用的 A4 橫式清冊 PDF（捐物人資料、支出明細）。
 *
 * <p>版面依 SPEC §6、§11：每頁重複文件抬頭與欄名、列不跨頁、最後是合計列，之後是「經手人／會計／負責人／團體用印」
 * 四格同排、每格至少 20mm 高、整組不跨頁；簽章與用印留白。頁尾只放活動簡稱、資料截止日與頁碼。
 * 表格文字至少 10pt。
 *
 * <p>文字用字形輪廓繪製（見 {@link GlyphOutlines}），字型沒有的字畫成「？」並回報，不留空白也不丟例外
 * （一個罕用字不該讓整份結案文件產不出來）。
 */
@org.springframework.stereotype.Component
public class PdfTableWriter {

    public enum Align { LEFT, CENTER, RIGHT }

    public record Column(String title, float weight, Align align) {
    }

    /**
     * @param headerLines 每頁都重複的抬頭文字（法人全銜、活動、許可、期間、截止日…）
     * @param totalRow    合計列（與欄數同長，可為 null）
     * @param footerLabel 頁尾文字（活動簡稱、階段、截止日），後面會接「第 n／N 頁」
     */
    public record Spec(String title, List<String> headerLines, List<Column> columns, List<List<String>> rows,
                       List<String> totalRow, String footerLabel) {
    }

    /** 產生結果與用不到的字（空字串代表全部畫得出來）。 */
    public record Result(byte[] pdf, String replacedChars, int pages) {
    }

    static final float W = PDRectangle.A4.getHeight();     // 橫式
    static final float H = PDRectangle.A4.getWidth();
    static final float MARGIN = 42.5f;                     // 15mm
    static final float FONT = 10f;
    static final float LEADING = 14f;
    static final float PAD = 4f;
    static final float FOOTER_SPACE = 26f;
    static final float SIGN_HEIGHT = 62f;                  // 約 22mm，超過 SPEC 的 20mm

    private final GlyphOutlines glyphs;

    public PdfTableWriter(GlyphOutlines glyphs) {
        this.glyphs = glyphs;
    }

    private record RowBlock(List<List<String>> cellLines, float height, boolean total) {
    }

    private record Page(List<RowBlock> rows, boolean signature) {
    }

    public Result render(Spec spec) {
        StringBuilder replaced = new StringBuilder();
        float tableWidth = W - 2 * MARGIN;
        float weightSum = (float) spec.columns().stream().mapToDouble(Column::weight).sum();
        float[] colWidth = new float[spec.columns().size()];
        for (int i = 0; i < colWidth.length; i++) {
            colWidth[i] = tableWidth * spec.columns().get(i).weight() / weightSum;
        }

        float headerHeight = headerHeight(spec, colWidth);
        float bodyLimit = MARGIN + FOOTER_SPACE;
        List<Page> pages = new ArrayList<>();
        List<RowBlock> current = new ArrayList<>();
        float y = H - MARGIN - headerHeight;

        List<RowBlock> blocks = new ArrayList<>();
        for (List<String> row : spec.rows()) {
            blocks.add(block(row, colWidth, false, replaced));
        }
        if (spec.totalRow() != null) {
            blocks.add(block(spec.totalRow(), colWidth, true, replaced));
        }
        for (RowBlock b : blocks) {
            if (y - b.height() < bodyLimit && !current.isEmpty()) {
                pages.add(new Page(current, false));
                current = new ArrayList<>();
                y = H - MARGIN - headerHeight;
            }
            current.add(b);
            y -= b.height();
        }
        boolean signatureFits = y - 18 - SIGN_HEIGHT >= bodyLimit;
        if (signatureFits) {
            pages.add(new Page(current, true));
        } else {
            pages.add(new Page(current, false));
            pages.add(new Page(List.of(), true));            // 簽章整組放新的一頁，那頁一樣有抬頭與欄名
        }

        try (PDDocument doc = new PDDocument()) {
            GlyphOutlines.Session g = glyphs.session(doc);
            for (int n = 0; n < pages.size(); n++) {
                PDPage page = new PDPage(new PDRectangle(W, H));
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    drawPage(g, cs, spec, colWidth, pages.get(n), n + 1, pages.size(), replaced);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return new Result(out.toByteArray(), dedupe(replaced), pages.size());
        } catch (IOException e) {
            throw new UncheckedIOException("產生清冊 PDF 失敗", e);
        }
    }

    // ------------------------------------------------------------ 版面計算

    private float headerHeight(Spec spec, float[] colWidth) {
        float tableWidth = 0;
        for (float w : colWidth) {
            tableWidth += w;
        }
        float h = 22;                                           // 標題
        for (String line : spec.headerLines()) {
            h += wrap(line, tableWidth, FONT + 0.5f, new StringBuilder()).size() * 13f;
        }
        h += 6 + columnHeaderHeight(spec, colWidth);
        return h;
    }

    private float columnHeaderHeight(Spec spec, float[] colWidth) {
        int lines = 1;
        for (int i = 0; i < colWidth.length; i++) {
            lines = Math.max(lines, wrap(spec.columns().get(i).title(), colWidth[i] - 2 * PAD, FONT, new StringBuilder()).size());
        }
        return lines * LEADING + 2 * PAD;
    }

    private RowBlock block(List<String> row, float[] colWidth, boolean total, StringBuilder replaced) {
        List<List<String>> lines = new ArrayList<>();
        int max = 1;
        for (int i = 0; i < colWidth.length; i++) {
            String text = i < row.size() ? row.get(i) : "";
            List<String> wrapped = wrap(text, colWidth[i] - 2 * PAD, FONT, replaced);
            lines.add(wrapped);
            max = Math.max(max, wrapped.size());
        }
        return new RowBlock(lines, max * LEADING + 2 * PAD, total);
    }

    /** 依寬度換行；字型沒有的字換成「？」。 */
    private List<String> wrap(String text, float width, float size, StringBuilder replaced) {
        String safe = safe(text, replaced);
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < safe.length(); ) {
            int cp = safe.codePointAt(i);
            i += Character.charCount(cp);
            String ch = new String(Character.toChars(cp));
            if (cp == '\n') {
                lines.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            if (cur.length() > 0 && glyphs.width(cur + ch, size) > width) {
                lines.add(cur.toString());
                cur.setLength(0);
            }
            cur.append(ch);
        }
        lines.add(cur.toString());
        return lines;
    }

    private String safe(String text, StringBuilder replaced) {
        if (text == null) {
            return "";
        }
        String missing = glyphs.missingChars(text);
        if (missing.isEmpty()) {
            return text;
        }
        replaced.append(missing);
        String result = text;
        for (int i = 0; i < missing.length(); ) {
            int cp = missing.codePointAt(i);
            i += Character.charCount(cp);
            result = result.replace(new String(Character.toChars(cp)), "？");
        }
        return result;
    }

    private static String dedupe(StringBuilder chars) {
        StringBuilder sb = new StringBuilder();
        chars.codePoints().distinct().forEach(sb::appendCodePoint);
        return sb.toString();
    }

    // ------------------------------------------------------------ 繪製

    private void drawPage(GlyphOutlines.Session g, PDPageContentStream cs, Spec spec, float[] colWidth, Page page,
                          int number, int count, StringBuilder replaced) throws IOException {
        float tableWidth = W - 2 * MARGIN;
        cs.setNonStrokingColor(0f, 0f, 0f);
        cs.setStrokingColor(0f, 0f, 0f);
        cs.setLineWidth(0.6f);

        float y = H - MARGIN;
        g.draw(cs, safe(spec.title(), replaced), MARGIN, y - 14, 16f);
        y -= 22;
        for (String line : spec.headerLines()) {
            for (String ln : wrap(line, tableWidth, FONT + 0.5f, replaced)) {
                g.draw(cs, ln, MARGIN, y - 10, FONT + 0.5f);
                y -= 13;
            }
        }
        y -= 6;

        // 欄名列
        float headHeight = columnHeaderHeight(spec, colWidth);
        float x = MARGIN;
        for (int i = 0; i < colWidth.length; i++) {
            cs.addRect(x, y - headHeight, colWidth[i], headHeight);
            cs.stroke();
            List<String> lines = wrap(spec.columns().get(i).title(), colWidth[i] - 2 * PAD, FONT, replaced);
            float ty = y - PAD - 10.5f;
            for (String ln : lines) {
                drawAligned(g, cs, ln, x, colWidth[i], ty, Align.CENTER);
                ty -= LEADING;
            }
            x += colWidth[i];
        }
        y -= headHeight;

        for (RowBlock b : page.rows()) {
            x = MARGIN;
            for (int i = 0; i < colWidth.length; i++) {
                cs.addRect(x, y - b.height(), colWidth[i], b.height());
                cs.stroke();
                float ty = y - PAD - 10.5f;
                for (String ln : b.cellLines().get(i)) {
                    drawAligned(g, cs, ln, x, colWidth[i], ty, b.total() && i == 0 ? Align.LEFT : spec.columns().get(i).align());
                    ty -= LEADING;
                }
                x += colWidth[i];
            }
            y -= b.height();
        }

        if (page.signature()) {
            y -= 18;
            String[] labels = {"經手人", "會計", "負責人", "團體用印"};
            float boxWidth = tableWidth / labels.length;
            for (int i = 0; i < labels.length; i++) {
                float bx = MARGIN + i * boxWidth;
                cs.addRect(bx, y - SIGN_HEIGHT, boxWidth, SIGN_HEIGHT);
                cs.stroke();
                g.draw(cs, labels[i], bx + PAD + 2, y - 14, FONT + 1);
            }
        }

        String footer = "%s　　第 %d／%d 頁".formatted(safe(spec.footerLabel(), replaced), number, count);
        cs.setNonStrokingColor(0.3f, 0.3f, 0.3f);
        g.draw(cs, footer, MARGIN, MARGIN - 2, 9f);
    }

    private void drawAligned(GlyphOutlines.Session g, PDPageContentStream cs, String text, float cellX, float cellWidth,
                             float baseline, Align align) throws IOException {
        float w = glyphs.width(text, FONT);
        float x = switch (align) {
            case LEFT -> cellX + PAD;
            case CENTER -> cellX + (cellWidth - w) / 2;
            case RIGHT -> cellX + cellWidth - PAD - w;
        };
        cs.setNonStrokingColor(0f, 0f, 0f);
        g.draw(cs, text, x, baseline, FONT);
    }
}
