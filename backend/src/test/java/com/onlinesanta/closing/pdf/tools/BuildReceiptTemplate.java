package com.onlinesanta.closing.pdf.tools;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/**
 * 一次性工具：由使用者提供的原始收據範本，產生專案使用的「去字＋統一字型標籤」範本。
 * 平常不需要執行，只有範本或字型要更換時才重做。完整步驟見同目錄的 README.md。
 *
 * <p>兩個階段：
 * <ol>
 *   <li>{@code extract 原始範本.pdf 工作目錄}：讀出範本每一段文字（內容、位置、字級、顏色）寫成 labels.tsv，
 *       並列出用到的所有字寫成 chars.txt（給 fontTools 裁出只含這些字的小字型）。</li>
 *   <li>{@code build 原始範本.pdf 工作目錄 labels.ttf 輸出.pdf}：把範本文字全部清掉，再用小字型把每一段文字
 *       當成「真正的文字」寫回原位置。小字型已事先裁好，所以直接整份嵌入（不經 PDFBox 的子集化，
 *       PDFBox 對這個字型子集化會錯字）。</li>
 * </ol>
 */
public final class BuildReceiptTemplate {

    private BuildReceiptTemplate() {
    }

    record Label(int page, String text, float x, float y, float size, float r, float g, float b) {
        String toTsv() {
            return String.join("\t", String.valueOf(page), String.valueOf(x), String.valueOf(y), String.valueOf(size),
                    String.valueOf(r), String.valueOf(g), String.valueOf(b), text);
        }

        static Label fromTsv(String line) {
            String[] f = line.split("\t", 8);
            return new Label(Integer.parseInt(f[0]), f[7], Float.parseFloat(f[1]), Float.parseFloat(f[2]),
                    Float.parseFloat(f[3]), Float.parseFloat(f[4]), Float.parseFloat(f[5]), Float.parseFloat(f[6]));
        }
    }

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "extract" -> extract(Path.of(args[1]), Path.of(args[2]));
            case "build" -> build(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]), Path.of(args[4]));
            default -> throw new IllegalArgumentException("第一個參數必須是 extract 或 build");
        }
    }

    static void extract(Path original, Path workDir) throws IOException {
        Files.createDirectories(workDir);
        List<Label> labels = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(original.toFile())) {
            int pageNo = 0;
            for (PDPage page : doc.getPages()) {
                labels.addAll(scan(doc, page, pageNo++, false));
            }
        }
        List<String> lines = new ArrayList<>();
        TreeSet<Integer> chars = new TreeSet<>();
        for (Label l : labels) {
            lines.add(l.toTsv());
            l.text().codePoints().forEach(chars::add);
        }
        Files.write(workDir.resolve("labels.tsv"), lines, StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        chars.forEach(sb::appendCodePoint);
        Files.writeString(workDir.resolve("chars.txt"), sb.toString(), StandardCharsets.UTF_8);
        System.out.println("標籤 " + labels.size() + " 段，用到 " + chars.size() + " 個不同的字");
    }

    static void build(Path original, Path workDir, Path labelFont, Path output) throws IOException {
        List<Label> labels = new ArrayList<>();
        for (String line : Files.readAllLines(workDir.resolve("labels.tsv"), StandardCharsets.UTF_8)) {
            labels.add(Label.fromTsv(line));
        }
        try (PDDocument doc = Loader.loadPDF(original.toFile())) {
            int pageNo = 0;
            List<PDPage> pages = new ArrayList<>();
            doc.getPages().forEach(pages::add);
            for (PDPage page : pages) {
                scan(doc, page, pageNo++, true);          // 清掉所有文字區塊
                page.getResources().getCOSObject().removeItem(COSName.FONT);
            }
            // 小字型整份嵌入（embedSubset=false），三頁共用同一個字型物件
            PDType0Font font;
            try (var in = Files.newInputStream(labelFont)) {
                font = PDType0Font.load(doc, in, false);
            }
            for (int i = 0; i < pages.size(); i++) {
                try (PDPageContentStream cs = new PDPageContentStream(
                        doc, pages.get(i), PDPageContentStream.AppendMode.APPEND, true, true)) {
                    for (Label l : labels) {
                        if (l.page() != i) {
                            continue;
                        }
                        cs.setNonStrokingColor(l.r(), l.g(), l.b());
                        cs.beginText();
                        cs.setFont(font, l.size());
                        cs.newLineAtOffset(l.x(), l.y());
                        cs.showText(l.text());
                        cs.endText();
                    }
                }
            }
            doc.save(output.toFile());
        }
        System.out.println("輸出 " + output + "（" + Files.size(output) + " bytes）");
    }

    /**
     * 掃描一頁的文字區塊。{@code strip} 為 true 時把含文字的區塊整個移除並回存內容流。
     * 同一區塊裡連續的 Tj 沒有各自的位置，靠前一段的寬度接續。
     */
    static List<Label> scan(PDDocument doc, PDPage page, int pageNo, boolean strip) throws IOException {
        List<Object> tokens = new PDFStreamParser(page).parse();
        List<Object> kept = new ArrayList<>(tokens.size());
        List<Label> labels = new ArrayList<>();

        PDFont font = null;
        float size = 0;
        float x = 0;
        float y = 0;
        float[] fill = {0, 0, 0};
        List<Object> block = null;

        for (int i = 0; i < tokens.size(); i++) {
            Object token = tokens.get(i);
            if (!(token instanceof Operator op)) {
                (block != null ? block : kept).add(token);
                continue;
            }
            switch (op.getName()) {
                case "rg" -> fill = new float[] {num(tokens, i, 3), num(tokens, i, 2), num(tokens, i, 1)};
                case "BT" -> block = new ArrayList<>();
                case "Tf" -> {
                    font = page.getResources().getFont((COSName) tokens.get(i - 2));
                    size = num(tokens, i, 1);
                }
                case "Tm" -> {
                    x = num(tokens, i, 2);
                    y = num(tokens, i, 1);
                }
                case "Tj" -> {
                    byte[] bytes = ((COSString) tokens.get(i - 1)).getBytes();
                    String text = decode(font, bytes);
                    if (!isBlank(text) && !isPlaceholder(text)) {
                        labels.add(new Label(pageNo, text, x, y, size, fill[0], fill[1], fill[2]));
                    }
                    x += advance(font, bytes) * size / 1000f;
                }
                default -> { }
            }
            if (block != null) {
                block.add(token);
                if (op.getName().equals("ET")) {
                    block = null;
                }
            } else {
                kept.add(token);
            }
        }
        if (strip) {
            PDStream stream = new PDStream(doc);
            try (OutputStream os = stream.createOutputStream(COSName.FLATE_DECODE)) {
                new ContentStreamWriter(os).writeTokens(kept);
            }
            page.setContents(stream);
        }
        return labels;
    }

    /** 範本上預印、要由每張收據自己內容取代的文字。 */
    private static boolean isPlaceholder(String text) {
        return text.contains("OS2026-000001")
                || text.startsWith("勸募活動：")
                || text.startsWith("許可勸募期間：")
                || text.equals("份");
    }

    private static boolean isBlank(String text) {
        return text.codePoints().allMatch(cp -> {
            int type = Character.getType(cp);
            return Character.isWhitespace(cp) || type == Character.SPACE_SEPARATOR || type == Character.CONTROL
                    || type == Character.FORMAT || type == Character.UNASSIGNED;
        });
    }

    private static float num(List<Object> tokens, int operatorIndex, int fromEnd) {
        return ((COSNumber) tokens.get(operatorIndex - fromEnd)).floatValue();
    }

    private static String decode(PDFont font, byte[] bytes) throws IOException {
        StringBuilder sb = new StringBuilder();
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        while (in.available() > 0) {
            String u = font.toUnicode(font.readCode(in));
            sb.append(u == null ? "" : u);
        }
        return sb.toString();
    }

    private static float advance(PDFont font, byte[] bytes) throws IOException {
        float total = 0;
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        while (in.available() > 0) {
            total += font.getWidth(font.readCode(in));
        }
        return total;
    }

    @SuppressWarnings("unused")
    private static byte[] toBytes(PDDocument doc) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.save(out);
        return out.toByteArray();
    }
}
