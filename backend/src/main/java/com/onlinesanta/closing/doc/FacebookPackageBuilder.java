package com.onlinesanta.closing.doc;

import static com.onlinesanta.closing.doc.ClosingNarrative.PENDING;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.springframework.stereotype.Component;

import com.onlinesanta.closing.Campaign;
import com.onlinesanta.closing.ClosingProperties;
import com.onlinesanta.closing.doc.ClosingDataset.GiftRow;
import com.onlinesanta.closing.doc.ClosingDataset.OrgGroup;
import com.onlinesanta.closing.doc.ClosingPackageBuilder.PackageFile;
import com.onlinesanta.closing.doc.PdfFlow.Align;
import com.onlinesanta.closing.doc.PdfFlow.Col;
import com.onlinesanta.closing.pdf.GlyphOutlines;

/**
 * Facebook 公開徵信包：貼文文字與 2160px 寬的徵信圖片（依 SPEC §9）。
 *
 * <p>公開內容只有：捐贈者的公開名稱（收據抬頭，否則帳號真實姓名，沒有就「善心人士」）、日期、物資、數量、時價、
 * 受贈機構名稱。<strong>不含證號、地址、電話、信箱、簽章、收據編號、孩子暱稱與願望標題</strong>（受益人識別）。
 *
 * <p>版面用 PDFBox 向量繪字畫成螢幕比例的頁面（720×900pt、字級大），再用 PDFRenderer 轉成 JPG
 * （不經 AWT 字型，Alpine 映像不需要字型函式庫）。第 1 頁是所得及收支摘要，之後是完整捐贈明細，
 * D 與併報再加受贈機構與財物使用。每頁頁尾有簡稱、階段、截止日與「第 x／N 頁」。
 */
@Component
public class FacebookPackageBuilder {

    /** 一則貼文放的圖片張數上限。實際上傳功能的上限沒有查證，採保守值，超過就分成「第01則…」（SPEC §9）。 */
    static final int IMAGES_PER_POST = 30;
    private static final float PAGE_W = 720;
    private static final float PAGE_H = 900;
    private static final float RENDER_SCALE = 3f;                   // 720pt × 3 = 2160px

    public record Built(List<PackageFile> files, List<TodoItem> todo) {
    }

    private final GlyphOutlines glyphs;
    private final ClosingProperties properties;
    private final int imagesPerPost;

    @org.springframework.beans.factory.annotation.Autowired
    public FacebookPackageBuilder(GlyphOutlines glyphs, ClosingProperties properties) {
        this(glyphs, properties, IMAGES_PER_POST);
    }

    /** 測試用：指定一則貼文的圖片張數上限。 */
    FacebookPackageBuilder(GlyphOutlines glyphs, ClosingProperties properties, int imagesPerPost) {
        this.glyphs = glyphs;
        this.properties = properties;
        this.imagesPerPost = imagesPerPost;
    }

    public Built build(ClosingDataset d) {
        String root = "02_交給康乃心/Facebook公開徵信/";
        String sub = switch (d.stage()) {
            case C -> "C_勸募期滿";
            case D -> "D_使用完竣";
            case CD -> "成果結案併報";
        };
        List<TodoItem> todo = new ArrayList<>();
        byte[] pdf = renderPages(d, todo);
        List<byte[]> images = toJpegs(pdf);
        int posts = Math.max(1, (images.size() + imagesPerPost - 1) / imagesPerPost);

        List<PackageFile> files = new ArrayList<>();
        String firstPostText = null;
        for (int p = 0; p < posts; p++) {
            String dir = root + sub + "/" + (posts == 1 ? "" : "第%02d則/".formatted(p + 1));
            List<byte[]> slice = images.subList(p * imagesPerPost, Math.min(images.size(), (p + 1) * imagesPerPost));
            String text = postText(d, p + 1, posts, slice.size(), images.size());
            if (p == 0) {
                firstPostText = text;
            }
            files.add(new PackageFile(dir + "01_貼文文字.txt", ("﻿" + text).getBytes(StandardCharsets.UTF_8)));
            for (int i = 0; i < slice.size(); i++) {
                files.add(new PackageFile(dir + "02_徵信圖片/%03d.jpg".formatted(p * imagesPerPost + i + 1), slice.get(i)));
            }
        }
        boolean pending = firstPostText.contains(PENDING);
        files.add(new PackageFile(root + "00_發文說明.html", guide(d, firstPostText, images.size(), posts, !pending)));

        todo.add(new TodoItem("Facebook 實際上傳功能一則可放幾張圖片（目前以每則 %d 張分則）".formatted(imagesPerPost),
                "協會（發文時確認）", root, "圖片張數與則數已列在發文說明頁；超過上限請告訴工程端調整"));
        if (pending) {
            todo.add(new TodoItem("貼文含尚未確認的項目（議決情形等）", "協會",
                    "Facebook 公開徵信包", "確認後重新產生，再發布；未備妥前發文說明頁的複製按鈕停用"));
        }
        todo.add(new TodoItem("公開徵信的真實發布日期、平台、網址與公開內容範圍", "協會（發布後填入照表操作頁的網址欄）",
                "成果報告、照表操作頁", "未發布即寫待發布，不虛構網址或截圖"));
        return new Built(files, todo);
    }

    // ------------------------------------------------------------ 圖片

    private byte[] renderPages(ClosingDataset d, List<TodoItem> todo) {
        Campaign c = d.campaign();
        String footer = "%s　%s　資料截止日 %s".formatted(c.getName(), d.stage().label(), RocDate.slash(d.cutoff()));
        try (PdfFlow f = new PdfFlow(glyphs, new PDRectangle(PAGE_W, PAGE_H), 40f, footer)) {
            // 第 1 頁：所得及收支摘要
            f.centered(c.getName(), 24);
            f.centered("公開徵信（" + d.stage().label() + "）", 18);
            f.space(8);
            f.field("勸募團體", properties.organizer(), 100, 15);
            f.field("活動名稱", c.getApprovedName() != null ? c.getApprovedName() : c.getName(), 100, 15);
            f.field("勸募許可文號", c.getPermitNo() == null || c.getPermitNo().isBlank() ? "待確認" : c.getPermitNo(), 100, 15);
            f.field("勸募期間", RocDate.full(c.getCollectionStart()) + "至" + RocDate.full(c.getCollectionEnd()), 100, 15);
            f.field("資料截止日", RocDate.full(d.cutoff()), 100, 15);
            f.space(12);
            f.heading("所得及收支", 20);
            f.table(List.of(new Col("項目", 3f, Align.LEFT), new Col("金額（元）", 2f, Align.RIGHT)),
                    List.of(List.of("實收禮物份數", String.valueOf(d.totals().quantity())),
                            List.of("物資所得（時價）", ClosingNarrative.money(d.totals().value())),
                            List.of("現金捐款", "0"),
                            List.of("利息收入", "0"),
                            List.of("募得財物支用（轉贈機構）", ClosingNarrative.money(d.totals().value())),
                            List.of("募得款實支（現金）", "0"),
                            List.of("本會自籌支應", "0"),
                            List.of("受贈機構數", String.valueOf(d.totals().organizationCount()))), 16, 30);
            f.space(8);
            f.paragraph("感謝每一位捐贈者。完整捐贈明細見後續圖片。", 14, 22);

            // 捐贈明細（公開名稱、日期、物資、數量、時價）
            f.newPage();
            f.heading("捐贈明細", 20);
            f.paragraph("捐贈用途：" + properties.purpose(), 13, 20);
            f.space(4);
            List<List<String>> rows = new ArrayList<>();
            int no = 0;
            for (GiftRow g : d.gifts()) {
                rows.add(List.of(String.valueOf(++no), g.donorName(), RocDate.slash(g.donatedOn()), g.itemName(),
                        g.quantity() + g.unit(), String.format("%,d", g.value())));
            }
            if (rows.isEmpty()) {
                rows.add(List.of("", "（本階段範圍內沒有捐贈）", "", "", "", ""));
            }
            f.table(List.of(new Col("序", 0.6f, Align.CENTER), new Col("捐贈者", 2f, Align.LEFT), new Col("日期", 1.4f, Align.CENTER),
                    new Col("物資", 3.4f, Align.LEFT), new Col("數量", 0.9f, Align.CENTER), new Col("時價（元）", 1.3f, Align.RIGHT)),
                    rows, 14, 26);

            // D 與併報：受贈機構與財物使用（只寫機構名稱，不寫受益人）
            if (d.stage().includesExpenses()) {
                f.newPage();
                f.heading("財物使用情形", 20);
                List<List<String>> orgs = new ArrayList<>();
                for (OrgGroup g : d.organizations()) {
                    orgs.add(List.of(g.name() + (g.association() ? "（本會服務對象）" : ""), String.valueOf(g.gifts().size()),
                            String.valueOf(g.quantity()), String.format("%,d", g.value())));
                }
                if (orgs.isEmpty()) {
                    orgs.add(List.of("（沒有受贈機構）", "0", "0", "0"));
                }
                f.table(List.of(new Col("受贈機構", 4f, Align.LEFT), new Col("禮物件數", 1.2f, Align.RIGHT),
                        new Col("份數", 1f, Align.RIGHT), new Col("轉贈時價（元）", 1.8f, Align.RIGHT)), orgs, 15, 28);
                f.space(8);
                f.paragraph("物資由捐贈者直接送至受贈機構，由機構向服務對象發放。", 14, 22);
            }
            byte[] pdf = f.finish();
            if (!f.replacedChars().isEmpty()) {
                todo.add(new TodoItem("徵信圖片的字型沒有這些字，已畫成「？」：" + f.replacedChars(), "工程（補字型）",
                        "Facebook 公開徵信圖片", "發布前請確認公開名稱顯示正確"));
            }
            return pdf;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** PDF 每頁轉成 2160px 寬的 JPG。 */
    private List<byte[]> toJpegs(byte[] pdf) {
        List<byte[]> images = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                BufferedImage image = renderer.renderImage(i, RENDER_SCALE, ImageType.RGB);
                images.add(jpeg(image));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return images;
    }

    private static byte[] jpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.88f);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------ 貼文文字

    String postText(ClosingDataset d, int post, int posts, int imagesInPost, int totalImages) {
        Campaign c = d.campaign();
        StringBuilder sb = new StringBuilder();
        String name = c.getApprovedName() != null ? c.getApprovedName() : c.getName();
        sb.append("【").append(name).append("　公開徵信（").append(d.stage().label()).append("）】");
        if (posts > 1) {
            sb.append("　第%d則／共%d則".formatted(post, posts));
        }
        sb.append("\n\n");
        sb.append(properties.organizer()).append("辦理「").append(name).append("」，勸募期間為")
                .append(c.getPermitNo() == null || c.getPermitNo().isBlank() ? PENDING
                        : RocDate.full(c.getCollectionStart()) + "至" + RocDate.full(c.getCollectionEnd()))
                .append("。感謝每一位捐贈者的愛心！\n\n");
        sb.append("截至").append(RocDate.full(d.cutoff())).append("：\n");
        sb.append("．實收禮物").append(d.totals().quantity()).append("份、物資時價").append(ClosingNarrative.money(d.totals().value())).append("元\n");
        sb.append("．現金捐款").append("0").append("元\n");
        sb.append("．利息收入").append("0").append("元\n");
        sb.append("．已轉贈").append(d.totals().organizationCount()).append("間受贈機構\n");
        if (d.stage().includesExpenses()) {
            sb.append("．本案理事會議決情形：").append(PENDING).append("\n");
        }
        sb.append("\n完整所得及收支與捐贈明細，請見").append(posts > 1 ? "本則" : "").append("圖片（共").append(posts > 1 ? imagesInPost : totalImages)
                .append("張，依編號順序閱讀）。\n");
        return sb.toString();
    }

    // ------------------------------------------------------------ 發文說明頁

    private byte[] guide(ClosingDataset d, String postText, int imageCount, int posts, boolean ready) {
        Document doc = Jsoup.parse(guideTemplate());
        doc.outputSettings().prettyPrint(false).escapeMode(Entities.EscapeMode.base).charset(StandardCharsets.UTF_8);
        var area = doc.getElementById("fb.post_text");
        if (area != null) {
            area.text(postText);
            area.attr("data-ready", String.valueOf(ready));
        }
        // 「本次公告」與圖片張數、貼文則數
        doc.select("*:matchesOwn(^\\s*【待確認】\\s*$)").stream().filter(e -> !e.tagName().equals("textarea")).findFirst()
                .ifPresent(e -> e.text(d.stage().label()));
        doc.select("*:containsOwn(本次圖片：)").forEach(e -> e.text("本次圖片：%d張；貼文：%d則。若需多則，工程師會先分包，不用自己拆圖。"
                .formatted(imageCount, posts)));
        doc.select("*:matchesOwn(^\\s*待產生\\s*$)").forEach(e -> e.text("已產生"));
        return ("<!doctype html>\n" + doc.outerHtml().replaceFirst("^<!doctype html>\\s*", "")).getBytes(StandardCharsets.UTF_8);
    }

    private static String guideTemplate() {
        try (InputStream in = FacebookPackageBuilder.class.getClassLoader().getResourceAsStream("closing/templates/facebook-guide.html")) {
            if (in == null) {
                throw new IllegalStateException("找不到 Facebook 發文說明範本");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
