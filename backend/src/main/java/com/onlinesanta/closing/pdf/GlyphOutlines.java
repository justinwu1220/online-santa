package com.onlinesanta.closing.pdf;

import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.fontbox.ttf.CmapLookup;
import org.apache.fontbox.ttf.GlyphData;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Component;

/**
 * 把中文字用「字形向量輪廓」畫進 PDF，而不是嵌入字型。
 *
 * <p>為什麼不嵌入：PDFBox 3.0.5／3.0.6 對楷體、Noto 這類字型做子集嵌入會產生錯字形（實測，
 * 整份字型嵌入才正常，但每份 PDF 要 5MB）。直接畫輪廓不依賴字型表格的細節，也不會有嵌入授權的疑慮
 * （字型採 SIL OFL，文件不受授權限制）。代價是文字不能選取或搜尋——收據與報表是印出來用的。
 *
 * <p>字形輪廓第一次用到才解析，之後從快取取；解析本身用鎖保護（fontbox 的 TrueTypeFont 不是執行緒安全的），
 * 繪製時只讀快取，可並行。
 */
@Component
public class GlyphOutlines {

    private static final String FONT_RESOURCE = "closing/fonts/TW-Kai-Subset.ttf";

    /** 一個字形的路徑指令：0 移動、1 直線、2 三次曲線、3 封閉。座標在字型單位（y 向上，與 PDF 同向）。 */
    private record Glyph(byte[] ops, float[] coords, int advance) {
    }

    private final TrueTypeFont font;
    private final CmapLookup cmap;
    private final float unitsPerEm;
    private final Map<Integer, Glyph> cache = new ConcurrentHashMap<>();

    public GlyphOutlines() {
        this(loadBundledFont());
    }

    /**
     * 用指定的字型繪字。正式環境只用上面預設的內建字型；這個建構子給字型預覽工具
     * （{@code tools/BuildFontSpecimen}）比較不同字型的收據外觀用。字型必須是 TrueType 輪廓（glyf）。
     */
    public GlyphOutlines(TrueTypeFont font) {
        this.font = font;
        try {
            this.cmap = font.getUnicodeCmapLookup();
            this.unitsPerEm = font.getUnitsPerEm();
        } catch (IOException e) {
            throw new UncheckedIOException("讀取字型失敗", e);
        }
    }

    private static TrueTypeFont loadBundledFont() {
        try (InputStream in = GlyphOutlines.class.getClassLoader().getResourceAsStream(FONT_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("找不到結案文件字型：" + FONT_RESOURCE);
            }
            return new TTFParser().parse(new RandomAccessReadBuffer(in));
        } catch (IOException e) {
            throw new UncheckedIOException("載入結案文件字型失敗", e);
        }
    }

    /** 字型沒有的字（會畫不出來）。空字串代表全部都有。 */
    public String missingChars(String text) {
        StringBuilder missing = new StringBuilder();
        text.codePoints().forEach(cp -> {
            if (!invisible(cp) && cmap.getGlyphId(cp) == 0 && missing.indexOf(new String(Character.toChars(cp))) < 0) {
                missing.appendCodePoint(cp);
            }
        });
        return missing.toString();
    }

    /** 空白、控制字元、格式字元：不需要字形。 */
    static boolean invisible(int cp) {
        int type = Character.getType(cp);
        return Character.isWhitespace(cp) || type == Character.SPACE_SEPARATOR || type == Character.CONTROL
                || type == Character.FORMAT || type == Character.UNASSIGNED;
    }

    /** 文字在指定字級下的寬度（pt）。 */
    public float width(String text, float size) {
        float units = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            units += glyph(cp).advance();
        }
        return units * size / unitsPerEm;
    }

    /**
     * 一份 PDF 文件專用的繪字工作階段：每個字形在這份文件裡<strong>只定義一次</strong>（表單物件），
     * 之後每次出現都只是「放置」，三頁共用。直接把路徑寫進每一頁會讓每張收據超過 1MB。
     */
    public final class Session {
        private final PDDocument document;
        private final Map<Integer, PDFormXObject> forms = new HashMap<>();

        private Session(PDDocument document) {
            this.document = document;
        }

        /** 在 (x, baselineY)（PDF 座標，y 向上）畫文字，用目前的填色。 */
        public void draw(PDPageContentStream cs, String text, float x, float baselineY, float size) throws IOException {
            float scale = size / unitsPerEm;
            float pen = x;
            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
                i += Character.charCount(cp);
                int gid = cmap.getGlyphId(cp);
                Glyph g = glyph(cp);
                if (g.ops().length > 0) {
                    PDFormXObject form = forms.get(gid);
                    if (form == null) {
                        form = buildForm(g);
                        forms.put(gid, form);
                    }
                    cs.saveGraphicsState();
                    cs.transform(new Matrix(scale, 0, 0, scale, r(pen), r(baselineY)));
                    cs.drawForm(form);
                    cs.restoreGraphicsState();
                }
                pen += g.advance() * scale;
            }
        }

        private PDFormXObject buildForm(Glyph g) throws IOException {
            // 用 PDAppearanceStream 當表單物件：它是 PDFormXObject 的子類，而且能指定輸出串流，
            // 讓字形內容以 Flate 壓縮（PDFormContentStream 預設不壓縮，每個字會大好幾倍）
            PDAppearanceStream form = new PDAppearanceStream(document);
            // 字形座標在字型單位內，留足邊界（上下伸出基線的字、特大字都不會被裁掉）
            form.setBBox(new PDRectangle(-unitsPerEm, -unitsPerEm, unitsPerEm * 3, unitsPerEm * 3));
            try (PDPageContentStream fs = new PDPageContentStream(document, form,
                    form.getStream().createOutputStream(COSName.FLATE_DECODE))) {
                int c = 0;
                for (byte op : g.ops()) {
                    switch (op) {
                        case 0 -> { fs.moveTo(Math.round(g.coords()[c]), Math.round(g.coords()[c + 1])); c += 2; }
                        case 1 -> { fs.lineTo(Math.round(g.coords()[c]), Math.round(g.coords()[c + 1])); c += 2; }
                        case 2 -> {
                            fs.curveTo(Math.round(g.coords()[c]), Math.round(g.coords()[c + 1]),
                                    Math.round(g.coords()[c + 2]), Math.round(g.coords()[c + 3]),
                                    Math.round(g.coords()[c + 4]), Math.round(g.coords()[c + 5]));
                            c += 6;
                        }
                        default -> fs.closePath();
                    }
                }
                fs.fill();
            }
            return form;
        }
    }

    public Session session(PDDocument document) {
        return new Session(document);
    }

    private static float r(float v) {
        return Math.round(v * 100f) / 100f;
    }

    private Glyph glyph(int codePoint) {
        int gid = cmap.getGlyphId(codePoint);
        if (gid == 0) {
            // 字型沒有這個字：什麼都不畫，只留空位。.notdef 的字形是一個方框，畫出來比空白更誤導
            // （會缺字的內容在渲染前就由 missingChars 擋下，這裡只會遇到空白與控制字元）
            return new Glyph(new byte[0], new float[0],
                    Math.round(codePoint == 0x3000 ? unitsPerEm : unitsPerEm / 2f));
        }
        return cache.computeIfAbsent(gid, this::load);
    }

    private Glyph load(int gid) {
        synchronized (font) {
            try {
                int advance = font.getAdvanceWidth(gid);
                GlyphData data = font.getGlyph().getGlyph(gid);
                if (data == null) {
                    return new Glyph(new byte[0], new float[0], advance);
                }
                GeneralPath path = data.getPath();
                List<Byte> ops = new ArrayList<>();
                List<Float> coords = new ArrayList<>();
                float[] c = new float[6];
                float cx = 0;
                float cy = 0;
                for (PathIterator it = path.getPathIterator(null); !it.isDone(); it.next()) {
                    switch (it.currentSegment(c)) {
                        case PathIterator.SEG_MOVETO -> {
                            ops.add((byte) 0);
                            add(coords, c[0], c[1]);
                            cx = c[0];
                            cy = c[1];
                        }
                        case PathIterator.SEG_LINETO -> {
                            ops.add((byte) 1);
                            add(coords, c[0], c[1]);
                            cx = c[0];
                            cy = c[1];
                        }
                        case PathIterator.SEG_QUADTO -> {
                            // 二次曲線轉三次：控制點取 2/3 處
                            ops.add((byte) 2);
                            add(coords, cx + 2f / 3f * (c[0] - cx), cy + 2f / 3f * (c[1] - cy),
                                    c[2] + 2f / 3f * (c[0] - c[2]), c[3] + 2f / 3f * (c[1] - c[3]), c[2], c[3]);
                            cx = c[2];
                            cy = c[3];
                        }
                        case PathIterator.SEG_CUBICTO -> {
                            ops.add((byte) 2);
                            add(coords, c[0], c[1], c[2], c[3], c[4], c[5]);
                            cx = c[4];
                            cy = c[5];
                        }
                        default -> ops.add((byte) 3);
                    }
                }
                byte[] opArray = new byte[ops.size()];
                for (int i = 0; i < opArray.length; i++) {
                    opArray[i] = ops.get(i);
                }
                float[] coordArray = new float[coords.size()];
                for (int i = 0; i < coordArray.length; i++) {
                    coordArray[i] = coords.get(i);
                }
                return new Glyph(opArray, coordArray, advance);
            } catch (IOException e) {
                throw new UncheckedIOException("讀取字形失敗：gid=" + gid, e);
            }
        }
    }

    private static void add(List<Float> list, float... values) {
        for (float v : values) {
            list.add(v);
        }
    }
}
