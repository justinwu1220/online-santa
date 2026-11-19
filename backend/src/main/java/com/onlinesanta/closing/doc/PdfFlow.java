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
 * 由上往下排版的 PDF 書寫器：標題、段落、鍵值表、表格、簽章格。換頁自動處理、頁尾自動加「第 n／N 頁」。
 * 成果報告與 05A／05B／05C 共用。
 *
 * <p>文字用字形輪廓繪製（見 {@link GlyphOutlines}），字型沒有的字畫成「？」並記錄在 {@link #replacedChars()}。
 * 一頁一頁的內容串流先不關閉，最後一起畫頁尾（才知道總頁數）。
 */
public final class PdfFlow implements AutoCloseable {

    public enum Align { LEFT, CENTER, RIGHT }

    public record Col(String title, float weight, Align align) {
    }

    private final GlyphOutlines glyphs;
    private final PDDocument doc = new PDDocument();
    private final GlyphOutlines.Session session;
    private final PDRectangle size;
    private final float margin;
    private final String footerLabel;
    private final StringBuilder replaced = new StringBuilder();
    private final List<PDPageContentStream> streams = new ArrayList<>();

    private PDPageContentStream cs;
    private float y;
    private int pageCount;
    private Runnable pageHeader;
    private float minRow;

    public PdfFlow(GlyphOutlines glyphs, PDRectangle size, float margin, String footerLabel) {
        this.glyphs = glyphs;
        this.session = glyphs.session(doc);
        this.size = size;
        this.margin = margin;
        this.footerLabel = footerLabel;
        newPage();
    }

    public float width() {
        return size.getWidth() - 2 * margin;
    }

    public float remaining() {
        return y - (margin + 24);
    }

    /** 每一頁開頭都要重畫的內容（例如文件抬頭）；設定後立刻在目前這頁畫一次。 */
    public void repeatOnEveryPage(Runnable header) {
        this.pageHeader = header;
        header.run();
    }

    public void newPage() {
        try {
            PDPage page = new PDPage(size);
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            streams.add(cs);
            pageCount++;
            y = size.getHeight() - margin;
            cs.setLineWidth(0.6f);
            if (pageHeader != null && pageCount > 1) {
                pageHeader.run();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void ensure(float height) {
        if (remaining() < height) {
            newPage();
        }
    }

    public void space(float points) {
        y -= points;
    }

    // ------------------------------------------------------------ 文字

    public void heading(String text, float fontSize) {
        ensure(fontSize + 10 + 70);          // 標題不要孤零零留在頁尾，後面至少要跟得上一小段內容
        draw(text, margin, y - fontSize, fontSize, 0f);
        y -= fontSize + 8;
    }

    public void paragraph(String text, float fontSize, float leading) {
        for (String line : wrap(text, width(), fontSize)) {
            ensure(leading);
            draw(line, margin, y - fontSize, fontSize, 0f);
            y -= leading;
        }
    }

    public void centered(String text, float fontSize) {
        ensure(fontSize + 6);
        float w = glyphs.width(safe(text), fontSize);
        draw(text, margin + (width() - w) / 2, y - fontSize, fontSize, 0f);
        y -= fontSize + 6;
    }

    /** 欄位列：「標籤：內容」，標籤固定寬度，內容換行對齊。 */
    public void field(String label, String value, float labelWidth, float fontSize) {
        List<String> lines = wrap(value, width() - labelWidth, fontSize);
        float leading = fontSize + 5;
        ensure(leading * lines.size());
        draw(label, margin, y - fontSize, fontSize, 0.25f);
        for (String line : lines) {
            draw(line, margin + labelWidth, y - fontSize, fontSize, 0f);
            y -= leading;
        }
    }

    // ------------------------------------------------------------ 表格

    public void table(List<Col> cols, List<List<String>> rows, float fontSize) {
        table(cols, rows, fontSize, 0);
    }

    /** 同上，每一列至少這麼高（留白給人手寫）。 */
    public void table(List<Col> cols, List<List<String>> rows, float fontSize, float minRowHeight) {
        this.minRow = minRowHeight;
        float total = (float) cols.stream().mapToDouble(Col::weight).sum();
        float[] w = new float[cols.size()];
        for (int i = 0; i < w.length; i++) {
            w[i] = width() * cols.get(i).weight() / total;
        }
        float pad = 4;
        float leading = fontSize + 4;
        List<String> head = cols.stream().map(Col::title).toList();
        float keepMin = minRow;
        minRow = 0;
        tableRow(head, w, cols, fontSize, pad, leading, true);
        minRow = keepMin;
        for (List<String> row : rows) {
            // 列太高放不下時換頁，新的一頁重畫欄名
            float h = rowHeight(row, w, fontSize, pad, leading);
            if (remaining() < h) {
                newPage();
                minRow = 0;
                tableRow(head, w, cols, fontSize, pad, leading, true);
                minRow = keepMin;
            }
            tableRow(row, w, cols, fontSize, pad, leading, false);
        }
    }

    private float rowHeight(List<String> row, float[] w, float fontSize, float pad, float leading) {
        int lines = 1;
        for (int i = 0; i < w.length; i++) {
            lines = Math.max(lines, wrap(i < row.size() ? row.get(i) : "", w[i] - 2 * pad, fontSize).size());
        }
        return Math.max(lines * leading + 2 * pad, minRow);
    }

    private void tableRow(List<String> row, float[] w, List<Col> cols, float fontSize, float pad, float leading,
                          boolean header) {
        float h = rowHeight(row, w, fontSize, pad, leading);
        ensure(h);
        try {
            float x = margin;
            for (int i = 0; i < w.length; i++) {
                cs.addRect(x, y - h, w[i], h);
                cs.stroke();
                float ty = y - pad - fontSize;
                for (String line : wrap(i < row.size() ? row.get(i) : "", w[i] - 2 * pad, fontSize)) {
                    float tw = glyphs.width(line, fontSize);
                    Align align = header ? Align.CENTER : cols.get(i).align();
                    float tx = switch (align) {
                        case LEFT -> x + pad;
                        case CENTER -> x + (w[i] - tw) / 2;
                        case RIGHT -> x + w[i] - pad - tw;
                    };
                    draw(line, tx, ty, fontSize, 0f);
                    ty -= leading;
                }
                x += w[i];
            }
            y -= h;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 簽章格：一排等寬的方格，每格標題在左上，下方留白（簽章與用印由人手填）。整組不跨頁。 */
    public void signatureRow(List<String> labels, float height, float fontSize) {
        ensure(height + 12);
        y -= 12;
        try {
            float bw = width() / labels.size();
            for (int i = 0; i < labels.size(); i++) {
                float bx = margin + i * bw;
                cs.addRect(bx, y - height, bw, height);
                cs.stroke();
                draw(labels.get(i), bx + 6, y - fontSize - 4, fontSize, 0f);
            }
            y -= height;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void rule() {
        ensure(8);
        try {
            cs.moveTo(margin, y - 3);
            cs.lineTo(margin + width(), y - 3);
            cs.stroke();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        y -= 8;
    }

    // ------------------------------------------------------------ 輸出

    public String replacedChars() {
        StringBuilder sb = new StringBuilder();
        replaced.codePoints().distinct().forEach(sb::appendCodePoint);
        return sb.toString();
    }

    public int pages() {
        return pageCount;
    }

    /** 加頁尾後輸出。 */
    public byte[] finish() {
        try {
            for (int i = 0; i < streams.size(); i++) {
                PDPageContentStream s = streams.get(i);
                s.setNonStrokingColor(0.3f, 0.3f, 0.3f);
                String footer = "%s　　第 %d／%d 頁".formatted(safe(footerLabel), i + 1, streams.size());
                session.draw(s, footer, margin, margin - 4, 9f);
                s.close();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("產生 PDF 失敗", e);
        }
    }

    @Override
    public void close() throws IOException {
        doc.close();
    }

    // ------------------------------------------------------------ 內部

    private void draw(String text, float x, float baseline, float fontSize, float gray) {
        try {
            cs.setNonStrokingColor(gray, gray, gray);
            session.draw(cs, safe(text), x, baseline, fontSize);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<String> wrap(String text, float width, float size) {
        String safe = safe(text);
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

    private String safe(String text) {
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
}
