package com.onlinesanta.closing.pdf.tools;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import javax.imageio.ImageIO;

import org.apache.fontbox.ttf.CmapLookup;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.util.Matrix;

import com.onlinesanta.closing.pdf.GlyphOutlines;
import com.onlinesanta.closing.pdf.ReceiptData;
import com.onlinesanta.closing.pdf.ReceiptLayoutException;
import com.onlinesanta.closing.pdf.ReceiptPdfRenderer;
import com.onlinesanta.closing.pdf.ReceiptTemplate;

/**
 * 收據字型預覽文件產生工具（只做文件，不改任何正式行為）。
 *
 * <p>讀入一批候選字型（TrueType 輪廓），每個字型產出一頁：授權與覆蓋字集統計、用該字型畫出的真實收據
 * （動態欄位）、固定標籤文字樣張、版面壓力測試（長姓名、數字、大寫金額、罕用字）。
 * 文件本身的說明文字用專案內建的霞鶩文楷。
 *
 * <p>用法：{@code java -Xmx3g -cp <classes;test-classes;依賴> ...BuildFontSpecimen 字型資料夾 輸出.pdf 預覽圖資料夾}
 * <br>字型資料夾放下載好的字型檔（檔名見 {@link #CANDIDATES}）；微軟字型直接讀 C:\Windows\Fonts，不複製。
 */
public final class BuildFontSpecimen {

    private BuildFontSpecimen() {
    }

    /** 一個候選字型。{@code file} 相對於字型資料夾；絕對路徑照用。 */
    record Candidate(String id, String name, String style, String file, String license, String source,
                     Verdict verdict, String note) {
    }

    enum Verdict {
        /** 授權允許放進程式庫與 PDF，可以採用。 */
        USABLE("可採用"),
        /** 現在正在使用。 */
        CURRENT("目前使用中"),
        /** 微軟等授權，只能在自己電腦預覽，不能放進系統。 */
        PREVIEW_ONLY("僅預覽，不可採用");

        final String label;

        Verdict(String label) {
            this.label = label;
        }
    }

    static final String WIN = "C:/Windows/Fonts/";

    static final List<Candidate> CANDIDATES = List.of(
            new Candidate("lxgw-regular", "霞鶩文楷 TC　Regular", "楷體（手寫楷書風）", "D:/Project/onlineSanta/backend/src/main/resources/closing/fonts/LXGWWenKaiTC-Subset.ttf",
                    "SIL OFL 1.1", "github.com/lxgw/LxgwWenKaiTC", Verdict.CURRENT,
                    "目前收據使用的字型（專案內的裁剪版，覆蓋字集就是現在實際能印的字）。筆畫較細、帶手寫感，與原版標楷體的方正感不同。"),
            new Candidate("lxgw-medium", "霞鶩文楷 TC　Medium", "楷體（較粗）", "LXGWWenKaiTC-Medium.ttf",
                    "SIL OFL 1.1", "github.com/lxgw/LxgwWenKaiTC", Verdict.USABLE,
                    "同一個字型家族的中等字重，筆畫較粗，印在收據上比 Regular 清楚。"),
            new Candidate("tw-kai", "全字庫正楷體 TW-Kai", "楷體（教科書楷書，最接近原版標楷體）", "TW-Kai.ttf",
                    "OFL 1.1 或 政府資料開放授權條款第 1 版（二選一）", "data.gov.tw/dataset/5961（行政院國發會 全字庫）",
                    Verdict.USABLE,
                    "政府提供的楷體，外觀最接近原版收據的標楷體。主檔只含 Unicode 基本區；擴充 B 區罕用字在另一個檔案 TW-Kai-Ext-B。"
                            + "檔案很大（約 37MB），採用時會裁成只含收據用字的小檔。"),
            new Candidate("tw-sung", "全字庫正宋體 TW-Sung", "宋體／明體", "TW-Sung.ttf",
                    "OFL 1.1 或 政府資料開放授權條款第 1 版（二選一）", "data.gov.tw/dataset/5961（行政院國發會 全字庫）",
                    Verdict.USABLE, "政府提供的明體。與楷體同一個資料集，授權相同。"),
            new Candidate("iansui", "芫荽 Iansui", "楷體（圓潤教科書風）", "iansui/Iansui-Regular.ttf",
                    "SIL OFL 1.1", "github.com/ButTaiwan/iansui", Verdict.USABLE,
                    "以日本 Klee One 為基礎、字形調整成台灣教育部標準的楷體風格，字形偏圓潤。"),
            new Candidate("noto-serif", "Noto Serif TC（思源宋體）", "宋體／明體（可變字型，只能取預設字重）",
                    "NotoSerifTC[wght].ttf", "SIL OFL 1.1", "fonts.google.com/noto/specimen/Noto+Serif+TC",
                    Verdict.USABLE, "常見的明體，閱讀性好。可變字型，系統只會讀到預設字重。"),
            new Candidate("noto-sans", "Noto Sans TC（思源黑體）", "黑體（可變字型，只能取預設字重）",
                    "NotoSansTC[wght].ttf", "SIL OFL 1.1", "fonts.google.com/noto/specimen/Noto+Sans+TC",
                    Verdict.USABLE, "黑體，與收據的正式感差距較大，列出供參考。"),
            new Candidate("cactus", "仙人掌明體 Cactus Classical Serif", "明體", "CactusClassicalSerif-Regular.ttf",
                    "SIL OFL 1.1", "fonts.google.com/specimen/Cactus+Classical+Serif", Verdict.USABLE,
                    "Google Fonts 收錄的繁體中文明體，筆畫比 Noto Serif 細緻。"),
            new Candidate("kaiu", "標楷體 DFKai-SB（Windows）", "楷體（原版收據使用的字型）", WIN + "kaiu.ttf",
                    "微軟隨 Windows 附贈，不可再散布", "Windows 內建", Verdict.PREVIEW_ONLY,
                    "原版空白收據用的就是這個字型。授權不允許放進程式庫或嵌入我們產生的 PDF，所以不能用；"
                            + "它的外觀請看緊接在總表後面的「原版收據」頁。"));

    /** 比較用的固定文字：收據上實際會出現的各種內容。 */
    static final String ASCII_SET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz,.-：、，。；（）／【】—";
    static final String VERTICAL_SET = "︰︵︶";
    static final String AMOUNT_SET = "零壹貳參肆伍陸柒捌玖拾佰仟萬元整";
    static final String RARE_SAMPLE = "𠮷鱻犇驫龘";

    /** 一個字型載入後的資訊。 */
    static final class Loaded implements AutoCloseable {
        final Candidate candidate;
        final TrueTypeFont font;
        final TrueTypeCollection collection;
        final long fileSize;
        final String error;
        // 覆蓋統計
        int commonTotal;
        int commonCovered;
        int lessCommonTotal;
        int lessCommonCovered;
        boolean extB;
        String missingLabels = "";
        String missingAscii = "";
        String missingAmount = "";
        String missingVertical = "";

        Loaded(Candidate candidate, TrueTypeFont font, TrueTypeCollection collection, long fileSize, String error) {
            this.candidate = candidate;
            this.font = font;
            this.collection = collection;
            this.fileSize = fileSize;
            this.error = error;
        }

        boolean ok() {
            return font != null;
        }

        @Override
        public void close() throws IOException {
            if (collection != null) {
                collection.close();
            } else if (font != null) {
                font.close();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("用法：BuildFontSpecimen <字型資料夾> <輸出.pdf> <預覽圖資料夾>");
            System.exit(2);
        }
        Path fontsDir = Path.of(args[0]);
        Path out = Path.of(args[1]);
        Path previews = Path.of(args[2]);
        Files.createDirectories(previews);

        ReceiptTemplate template = new ReceiptTemplate();
        String labelChars = labelChars(template);
        List<Loaded> fonts = new ArrayList<>();
        for (Candidate c : CANDIDATES) {
            Loaded loaded = load(c, fontsDir);
            if (loaded.ok()) {
                analyze(loaded, labelChars);
            }
            fonts.add(loaded);
            System.out.printf("%s：%s%n", c.name(), loaded.ok() ? "已載入" : loaded.error);
        }

        List<PDDocument> keepOpen = new ArrayList<>();
        try (PDDocument doc = new PDDocument()) {
            GlyphOutlines chrome = new GlyphOutlines();
            GlyphOutlines.Session ui = chrome.session(doc);
            Doc d = new Doc(doc, chrome, ui);

            summaryPages(d, fonts);
            originalPage(d, keepOpen);
            for (Loaded f : fonts) {
                if (f.ok() && f.candidate.verdict() != Verdict.PREVIEW_ONLY) {
                    fontPage(d, f, template, keepOpen);
                }
            }
            closingPage(d);
            doc.save(out.toFile());
        } finally {
            for (PDDocument k : keepOpen) {
                k.close();
            }
            for (Loaded f : fonts) {
                f.close();
            }
        }

        try (PDDocument saved = Loader.loadPDF(out.toFile())) {
            PDFRenderer renderer = new PDFRenderer(saved);
            for (int i = 0; i < saved.getNumberOfPages(); i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 110);
                ImageIO.write(image, "png", previews.resolve("page-%02d.png".formatted(i + 1)).toFile());
            }
            System.out.println("頁數：" + saved.getNumberOfPages() + "，檔案：" + Files.size(out) / 1024 + " KB");
        }
    }

    // ------------------------------------------------------------ 載入與統計

    static Loaded load(Candidate c, Path fontsDir) {
        Path path = Path.of(c.file()).isAbsolute() ? Path.of(c.file()) : fontsDir.resolve(c.file());
        File file = path.toFile();
        if (!file.exists()) {
            return new Loaded(c, null, null, 0, "找不到檔案：" + path);
        }
        try {
            if (file.getName().toLowerCase().endsWith(".ttc")) {
                TrueTypeCollection collection = new TrueTypeCollection(file);
                List<TrueTypeFont> members = new ArrayList<>();
                collection.processAllFonts(members::add);
                return new Loaded(c, members.get(0), collection, file.length(), null);
            }
            TrueTypeFont font = new TTFParser().parse(new RandomAccessReadBufferedFile(file));
            return new Loaded(c, font, null, file.length(), null);
        } catch (IOException | RuntimeException e) {
            return new Loaded(c, null, null, file.length(), "載入失敗：" + e.getMessage());
        }
    }

    /** 收據範本上所有固定標籤用到的字。 */
    static String labelChars(ReceiptTemplate template) throws IOException {
        try (PDDocument doc = Loader.loadPDF(template.pdf())) {
            String text = new PDFTextStripper().getText(doc);
            TreeSet<Integer> set = new TreeSet<>();
            text.codePoints().filter(cp -> !Character.isWhitespace(cp)).forEach(set::add);
            StringBuilder sb = new StringBuilder();
            set.forEach(sb::appendCodePoint);
            return sb.toString();
        }
    }

    static void analyze(Loaded l, String labelChars) throws IOException {
        CmapLookup cmap = l.font.getUnicodeCmapLookup();
        Charset big5 = Charset.forName("Big5");
        int[] common = countBig5(cmap, big5, 0xA4, 0xC6);
        int[] less = countBig5(cmap, big5, 0xC9, 0xF9);
        l.commonTotal = common[0];
        l.commonCovered = common[1];
        l.lessCommonTotal = less[0];
        l.lessCommonCovered = less[1];
        l.extB = cmap.getGlyphId(0x20BB7) != 0;
        l.missingLabels = missing(cmap, labelChars);
        l.missingAscii = missing(cmap, ASCII_SET);
        l.missingAmount = missing(cmap, AMOUNT_SET);
        l.missingVertical = missing(cmap, VERTICAL_SET);
    }

    /** Big5 常用字（A440–C67E）與次常用字（C940–F9D5）的覆蓋數。 */
    static int[] countBig5(CmapLookup cmap, Charset big5, int firstLead, int lastLead) {
        int total = 0;
        int covered = 0;
        for (int lead = firstLead; lead <= lastLead; lead++) {
            for (int trail = 0x40; trail <= 0xFE; trail++) {
                if (trail > 0x7E && trail < 0xA1) {
                    continue;
                }
                String s = new String(new byte[] {(byte) lead, (byte) trail}, big5);
                if (s.length() != 1 || s.charAt(0) == '�' || s.charAt(0) < 0x4E00) {
                    continue;
                }
                total++;
                if (cmap.getGlyphId(s.charAt(0)) != 0) {
                    covered++;
                }
            }
        }
        return new int[] {total, covered};
    }

    static String missing(CmapLookup cmap, String chars) {
        StringBuilder sb = new StringBuilder();
        chars.codePoints().forEach(cp -> {
            if (!Character.isWhitespace(cp) && cmap.getGlyphId(cp) == 0) {
                sb.appendCodePoint(cp);
            }
        });
        return sb.toString();
    }

    // ------------------------------------------------------------ 繪製

    static final float W = PDRectangle.A4.getWidth();
    static final float H = PDRectangle.A4.getHeight();
    static final float MARGIN = 40;

    /** 文件的繪製環境：說明文字一律用內建字型。 */
    record Doc(PDDocument doc, GlyphOutlines chrome, GlyphOutlines.Session ui) {
    }

    /** 一頁的游標。 */
    static final class Page {
        final PDPageContentStream cs;
        float y;

        Page(PDDocument doc) throws IOException {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            this.cs = new PDPageContentStream(doc, page);
            this.y = H - MARGIN;
        }
    }

    static void gray(Page p, float v) throws IOException {
        p.cs.setNonStrokingColor(v, v, v);
    }

    static void color(Page p, float r, float g, float b) throws IOException {
        p.cs.setNonStrokingColor(r, g, b);
    }

    /** 畫一行說明文字（內建字型），回傳實際使用的字級下的寬度。 */
    static void line(Doc d, Page p, String text, float x, float size) throws IOException {
        d.ui().draw(p.cs, text, x, p.y, size);
    }

    /** 依寬度換行畫字，游標往下移。 */
    static void para(Doc d, Page p, String text, float x, float width, float size, float leading) throws IOException {
        for (String ln : wrap(d.chrome(), text, width, size)) {
            line(d, p, ln, x, size);
            p.y -= leading;
        }
    }

    static List<String> wrap(GlyphOutlines g, String text, float width, float size) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            String ch = new String(Character.toChars(cp));
            if (ch.equals("\n")) {
                lines.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            if (cur.length() > 0 && g.width(cur + ch, size) > width) {
                lines.add(cur.toString());
                cur.setLength(0);
            }
            cur.append(ch);
        }
        if (cur.length() > 0) {
            lines.add(cur.toString());
        }
        return lines;
    }

    static String pct(int covered, int total) {
        return total == 0 ? "—" : "%d / %d（%.1f%%）".formatted(covered, total, covered * 100.0 / total);
    }

    static void summaryPages(Doc d, List<Loaded> fonts) throws IOException {
        Page p = new Page(d.doc());
        gray(p, 0);
        line(d, p, "收據字型預覽", MARGIN, 24);
        p.y -= 26;
        gray(p, 0.35f);
        line(d, p, "產生日期 " + LocalDate.now() + "　　線上聖誕老人・結案文件", MARGIN, 10);
        p.y -= 26;
        gray(p, 0);
        para(d, p, "這份文件列出所有可以用在捐贈收據上的候選字型，每個字型一頁，每一頁的收據都是「整張」用該字型真的畫出來的"
                + "（標題、欄位名稱、備註、直排文字與填入的內容全部是同一個字型），讓你直接比較外觀。請挑出想使用的字型（可以挑一個用在全部文字，也可以標題與內文各挑一個），"
                + "確定之後才會修改收據。目前還沒有更動任何收據字型。", MARGIN, W - 2 * MARGIN, 11, 17);
        p.y -= 6;
        para(d, p, "怎麼看：每頁上方是授權與覆蓋字集統計；中間是整張收據；最下方是長姓名、數字、大寫金額與罕用字的壓力測試。"
                + "字寬與霞鶩文楷不同的字型，個別欄位可能略有擠壓或偏移，那是預覽沒有逐項校正的結果，選定後會調整。"
                + "「常用字／次常用字覆蓋」用 Big5 的 5401 個常用字與 7652 個次常用字計算，數字越高越不會遇到缺字。",
                MARGIN, W - 2 * MARGIN, 11, 17);
        p.y -= 10;

        // 表頭
        float[] x = {MARGIN, 190, 300, 392, 470};
        gray(p, 0.35f);
        String[] head = {"字型", "風格", "授權", "常用字覆蓋", "判定"};
        for (int i = 0; i < head.length; i++) {
            d.ui().draw(p.cs, head[i], x[i], p.y, 9.5f);
        }
        p.y -= 6;
        p.cs.setStrokingColor(0.6f, 0.6f, 0.6f);
        p.cs.moveTo(MARGIN, p.y);
        p.cs.lineTo(W - MARGIN, p.y);
        p.cs.stroke();
        p.y -= 14;

        for (Loaded f : fonts) {
            Candidate c = f.candidate;
            List<String> nameLines = wrap(d.chrome(), c.name(), 140, 9.5f);
            List<String> styleLines = wrap(d.chrome(), c.style(), 104, 9);
            List<String> licenseLines = wrap(d.chrome(), shortLicense(c.license()), 86, 8.5f);
            int rows = Math.max(Math.max(nameLines.size(), styleLines.size()), licenseLines.size());
            if (p.y - rows * 12 < MARGIN) {
                p.cs.close();
                p = new Page(d.doc());
            }
            gray(p, 0);
            for (int i = 0; i < nameLines.size(); i++) {
                d.ui().draw(p.cs, nameLines.get(i), x[0], p.y - i * 12, 9.5f);
            }
            gray(p, 0.25f);
            for (int i = 0; i < styleLines.size(); i++) {
                d.ui().draw(p.cs, styleLines.get(i), x[1], p.y - i * 12, 9);
            }
            for (int i = 0; i < licenseLines.size(); i++) {
                d.ui().draw(p.cs, licenseLines.get(i), x[2], p.y - i * 12, 8.5f);
            }
            gray(p, 0);
            d.ui().draw(p.cs, f.ok() ? "%.1f%%".formatted(f.commonCovered * 100.0 / Math.max(1, f.commonTotal)) : "載入失敗",
                    x[3], p.y, 9);
            switch (c.verdict()) {
                case CURRENT -> color(p, 0.1f, 0.4f, 0.7f);
                case USABLE -> color(p, 0.1f, 0.5f, 0.2f);
                case PREVIEW_ONLY -> color(p, 0.75f, 0.15f, 0.15f);
            }
            d.ui().draw(p.cs, c.verdict().label, x[4], p.y, 9);
            p.y -= rows * 12 + 8;
        }
        p.cs.close();
    }

    static String shortLicense(String license) {
        if (license.startsWith("OFL 1.1 或")) {
            return "OFL 1.1 或政府開放授權（二選一）";
        }
        if (license.startsWith("微軟")) {
            return "微軟，不可散布";
        }
        return license;
    }

    static void fontPage(Doc d, Loaded f, ReceiptTemplate template, List<PDDocument> keepOpen) throws IOException {
        Candidate c = f.candidate;
        GlyphOutlines glyphs = new GlyphOutlines(f.font);
        GlyphOutlines.Session sample = glyphs.session(d.doc());
        Page p = new Page(d.doc());

        gray(p, 0);
        line(d, p, c.name(), MARGIN, 18);
        p.y -= 18;
        gray(p, 0.3f);
        line(d, p, c.style(), MARGIN, 10);
        p.y -= 15;
        switch (c.verdict()) {
            case CURRENT -> color(p, 0.1f, 0.4f, 0.7f);
            case USABLE -> color(p, 0.1f, 0.5f, 0.2f);
            case PREVIEW_ONLY -> color(p, 0.75f, 0.15f, 0.15f);
        }
        line(d, p, "判定：" + c.verdict().label, MARGIN, 10.5f);
        gray(p, 0.2f);
        p.y -= 14;
        para(d, p, "授權：" + c.license() + "　　來源：" + c.source(), MARGIN, W - 2 * MARGIN, 9, 12.5f);
        para(d, p, "檔案大小：%.1f MB　　常用字覆蓋：%s　　次常用字覆蓋：%s　　罕用字（擴充 B 區）：%s".formatted(
                f.fileSize / 1048576.0, pct(f.commonCovered, f.commonTotal), pct(f.lessCommonCovered, f.lessCommonTotal),
                f.extB ? "有" : "沒有"), MARGIN, W - 2 * MARGIN, 9, 12.5f);
        para(d, p, "收據固定標籤用字缺字：" + orNone(f.missingLabels) + "　　英數與符號缺字：" + orNone(f.missingAscii)
                + "　　大寫金額缺字：" + orNone(f.missingAmount) + "　　直排符號缺字：" + orNone(f.missingVertical),
                MARGIN, W - 2 * MARGIN, 9, 12.5f);
        para(d, p, c.note(), MARGIN, W - 2 * MARGIN, 9, 12.5f);
        p.y -= 4;

        // 真實收據
        ReceiptData data = sampleData();
        boolean receiptOk = true;
        try {
            byte[] templateBytes = fullTemplate(template, glyphs);
            ReceiptTemplate wholeFont = new ReceiptTemplate() {
                @Override
                public byte[] pdf() {
                    return templateBytes;
                }
            };
            ReceiptPdfRenderer renderer = new ReceiptPdfRenderer(wholeFont, glyphs);
            ReceiptPdfRenderer.Rendered rendered = renderer.render(data);
            PDDocument receiptDoc = Loader.loadPDF(rendered.white());
            keepOpen.add(receiptDoc);
            PDFormXObject form = new LayerUtility(d.doc()).importPageAsForm(receiptDoc, 0);
            float scale = (W - 2 * MARGIN) / 595.28f;
            float h = 446.46f * scale;
            gray(p, 0.35f);
            line(d, p, "整張收據都用這個字型畫出（標題、欄位名稱、備註、直排文字與填入的內容；只有左上角的圖樣與表格線是原範本）", MARGIN, 9);
            p.y -= 6;
            p.cs.saveGraphicsState();
            p.cs.transform(new Matrix(scale, 0, 0, scale, MARGIN, p.y - h));
            p.cs.drawForm(form);
            p.cs.restoreGraphicsState();
            p.cs.setStrokingColor(0.75f, 0.75f, 0.75f);
            p.cs.addRect(MARGIN, p.y - h, W - 2 * MARGIN, h);
            p.cs.stroke();
            p.y -= h + 16;
        } catch (ReceiptLayoutException e) {
            receiptOk = false;
            color(p, 0.75f, 0.15f, 0.15f);
            para(d, p, "這個字型無法畫出完整收據：" + e.getMessage(), MARGIN, W - 2 * MARGIN, 10, 14);
            p.y -= 6;
        }

        // 壓力測試
        gray(p, 0.35f);
        line(d, p, "版面壓力測試（10.5pt）", MARGIN, 9);
        p.y -= 15;
        gray(p, 0);
        specimenLine(d, p, sample, glyphs, "姓名　王小明　歐陽慕容　財團法人台灣兒童少年關懷發展協會", 10.5f);
        specimenLine(d, p, sample, glyphs, "物資　兒童益智積木玩具組（含說明書與收納袋，適合四歲以上）", 10.5f);
        specimenLine(d, p, sample, glyphs, "編號 OS2026-000001　證號 A123456789　統編 94558583　金額 9,999,999　民國115年11月25日", 10.5f);
        specimenLine(d, p, sample, glyphs, "大寫　零壹貳參肆伍陸柒捌玖　拾佰仟萬　元整", 10.5f);
        specimenLine(d, p, sample, glyphs, "直排符號　︰︵︶　　罕用字　" + RARE_SAMPLE, 10.5f);
        gray(p, 0.35f);
        if (!receiptOk || !f.missingLabels.isEmpty()) {
            color(p, 0.75f, 0.15f, 0.15f);
            line(d, p, "※ 缺字的地方會是空白，採用前需要補字或換字型", MARGIN, 8.5f);
        }
        p.cs.close();
    }

    /** 使用者提供的原版空白收據（標楷體）：判斷「像不像原版」的基準。 */
    static void originalPage(Doc d, List<PDDocument> keepOpen) throws IOException {
        Path original = Path.of("C:/Users/z8912/Downloads/結案文件/01_製作素材/康乃心_活動捐贈收據_三聯空白版.pdf");
        if (!Files.exists(original)) {
            return;
        }
        Page p = new Page(d.doc());
        gray(p, 0);
        line(d, p, "原版收據（標楷體）", MARGIN, 18);
        p.y -= 20;
        gray(p, 0.3f);
        para(d, p, "這是協會原本使用的空白三聯收據（白聯），文字是微軟標楷體。標楷體不能放進系統或嵌入我們產生的 PDF，"
                + "所以只能拿來當外觀基準：看下面各字型頁的收據，哪個最接近這個樣子。", MARGIN, W - 2 * MARGIN, 10, 14);
        p.y -= 6;
        PDDocument source = Loader.loadPDF(original.toFile());
        keepOpen.add(source);
        PDFormXObject form = new LayerUtility(d.doc()).importPageAsForm(source, 0);
        float scale = (W - 2 * MARGIN) / 595.28f;
        float h = 446.46f * scale;
        p.cs.saveGraphicsState();
        p.cs.transform(new Matrix(scale, 0, 0, scale, MARGIN, p.y - h));
        p.cs.drawForm(form);
        p.cs.restoreGraphicsState();
        p.cs.setStrokingColor(0.75f, 0.75f, 0.75f);
        p.cs.addRect(MARGIN, p.y - h, W - 2 * MARGIN, h);
        p.cs.stroke();
        p.cs.close();
    }

    /**
     * 把範本上所有固定文字清掉，改用候選字型把每一段原樣畫回原位置（位置、字級、顏色都照原範本），
     * 得到「整個收據都是這個字型」的範本。字型的字寬和霞鶩文楷不同時，個別欄位可能略有擠壓或偏移，
     * 採用字型時會逐項校正。
     */
    static byte[] fullTemplate(ReceiptTemplate base, GlyphOutlines glyphs) throws IOException {
        try (PDDocument doc = Loader.loadPDF(base.pdf())) {
            GlyphOutlines.Session session = glyphs.session(doc);
            List<PDPage> pages = new ArrayList<>();
            doc.getPages().forEach(pages::add);
            // 文字的位置、字級與顏色取自使用者的原始範本（專案內的範本用不同寫法，讀不到座標）；
            // 專案範本只拿來清掉文字、當底圖
            List<List<BuildReceiptTemplate.Label>> labelsByPage = originalLabels();
            for (int i = 0; i < pages.size(); i++) {
                BuildReceiptTemplate.scan(doc, pages.get(i), i, true);
            }
            for (int i = 0; i < pages.size(); i++) {
                try (PDPageContentStream cs = new PDPageContentStream(
                        doc, pages.get(i), PDPageContentStream.AppendMode.APPEND, true, true)) {
                    for (BuildReceiptTemplate.Label l : labelsByPage.get(i)) {
                        cs.setNonStrokingColor(l.r(), l.g(), l.b());
                        session.draw(cs, l.text(), l.x(), l.y(), l.size());
                    }
                }
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static List<List<BuildReceiptTemplate.Label>> originalLabelsCache;

    static synchronized List<List<BuildReceiptTemplate.Label>> originalLabels() throws IOException {
        if (originalLabelsCache == null) {
            Path original = Path.of("C:/Users/z8912/Downloads/結案文件/01_製作素材/康乃心_活動捐贈收據_三聯空白版.pdf");
            List<List<BuildReceiptTemplate.Label>> result = new ArrayList<>();
            try (PDDocument source = Loader.loadPDF(original.toFile())) {
                int i = 0;
                for (PDPage page : source.getPages()) {
                    result.add(BuildReceiptTemplate.scan(source, page, i++, false));
                }
            }
            originalLabelsCache = result;
        }
        return originalLabelsCache;
    }

    static String orNone(String s) {
        return s.isEmpty() ? "無" : s;
    }

    /** 用候選字型畫一行；有缺字就提醒。 */
    static void specimenLine(Doc d, Page p, GlyphOutlines.Session sample, GlyphOutlines glyphs, String text, float size)
            throws IOException {
        float width = glyphs.width(text, size);
        float use = size;
        float maxWidth = W - 2 * MARGIN;
        if (width > maxWidth) {
            use = size * maxWidth / width;
        }
        gray(p, 0);
        sample.draw(p.cs, text, MARGIN, p.y, use);
        String missing = glyphs.missingChars(text);
        if (!missing.isEmpty()) {
            color(p, 0.75f, 0.15f, 0.15f);
            d.ui().draw(p.cs, "缺字 " + codePoints(missing), W - MARGIN - 110, p.y + size + 1, 7);
        }
        p.y -= size + 6;
    }

    static String codePoints(String chars) {
        StringBuilder sb = new StringBuilder();
        chars.codePoints().forEach(cp -> sb.append("U+").append(Integer.toHexString(cp).toUpperCase()).append(' '));
        return sb.toString().trim();
    }

    static ReceiptData sampleData() {
        return new ReceiptData("OS2026-000042", LocalDate.of(2026, 12, 28), "王小明", "A123456789",
                LocalDate.of(2026, 12, 25), "線上聖誕老人2026聖誕禮物勸募活動", "衛部救字第1150000號",
                LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25), "兒童聖誕禮物",
                "兒童益智積木玩具組（含說明書與收納袋）", 12, "份", 9600, "財團法人快樂兒童之家");
    }

    static void closingPage(Doc d) throws IOException {
        Page p = new Page(d.doc());
        gray(p, 0);
        line(d, p, "授權與採用提醒", MARGIN, 20);
        p.y -= 28;
        para(d, p, "一、授權：標示「可採用」的字型，授權都允許放進程式庫、嵌入 PDF 與商業使用（OFL 1.1 需保留版權聲明與授權原文；"
                + "全字庫在 OFL 1.1 與政府資料開放授權條款之間二選一，我們會選 OFL 1.1 並附授權原文）。"
                + "標示「僅預覽」的微軟字型只能在你自己的電腦上看，不能放進系統，也不能嵌入我們產生的 PDF。", MARGIN, W - 2 * MARGIN, 11, 17);
        p.y -= 6;
        para(d, p, "二、教育部標準楷書沒有列入：它的授權是「姓名標示—禁止改作」，我們需要把字型裁成小檔或轉成向量輪廓，"
                + "這算不算「改作」需要向教育部確認，所以先不納入預覽。如果你特別喜歡它的外觀，可以告訴我，我再去確認授權。", MARGIN, W - 2 * MARGIN, 11, 17);
        p.y -= 6;
        para(d, p, "三、罕用字：多數字型的主檔不含 Unicode 擴充 B 區以後的字（例如「𠮷」）。收據遇到字型沒有的字時，"
                + "目前會標示「PDF 失敗」並說明缺哪些字，不會印出方框或錯字。全字庫另有擴充 B 區的字型檔，若你希望罕用字也能印出來，"
                + "可以在選定全字庫之後一併處理。", MARGIN, W - 2 * MARGIN, 11, 17);
        p.y -= 6;
        para(d, p, "四、換字型要做的事：選定後，我會把字型裁成只含收據用字的小檔放進專案、重建收據範本的固定標籤，"
                + "並重新檢查長姓名與長物資名稱的版面。這些都不會影響已經開立的收據資料，只是重新產生 PDF 時換了字型。", MARGIN, W - 2 * MARGIN, 11, 17);
        p.y -= 6;
        para(d, p, "五、想自己安裝看看：字型檔我下載在暫存資料夾，預覽完會刪除。想安裝體驗的話，可以到各字型的來源頁面自行下載。", MARGIN, W - 2 * MARGIN, 11, 17);
        p.cs.close();
    }
}
