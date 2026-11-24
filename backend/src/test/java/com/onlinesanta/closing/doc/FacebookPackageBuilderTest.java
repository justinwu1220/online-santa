package com.onlinesanta.closing.doc;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.onlinesanta.closing.Campaign;
import com.onlinesanta.closing.CampaignStatus;
import com.onlinesanta.closing.ClosingProperties;
import com.onlinesanta.closing.doc.ClosingDataset.GiftRow;
import com.onlinesanta.closing.doc.ClosingDataset.OrgGroup;
import com.onlinesanta.closing.doc.ClosingDataset.Totals;
import com.onlinesanta.closing.doc.ClosingPackageBuilder.PackageFile;
import com.onlinesanta.closing.pdf.GlyphOutlines;

@DisplayName("Facebook 公開徵信包")
class FacebookPackageBuilderTest {

    private static final GlyphOutlines GLYPHS = new GlyphOutlines();
    private static final ClosingProperties PROPS = new ClosingProperties(30, "本活動服務對象之個人聖誕禮物", "社團法人彰化縣康乃心關懷協會", 500_000);

    private static ClosingDataset dataset(int gifts) {
        Campaign c = new Campaign("線上聖誕老人 2026", LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25),
                LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25), LocalDate.of(2026, 11, 25),
                LocalDate.of(2027, 1, 20), "OS2026", 1);
        c.update(c.getName(), "115000877", "衛部救字第1150000號", null, c.getCollectionStart(), c.getCollectionEnd(),
                c.getUseStart(), c.getUseEnd(), c.getWishListStart(), c.getWishListEnd(), "OS2026", null, null,
                CampaignStatus.ACTIVE);
        UUID org = UUID.randomUUID();
        List<GiftRow> rows = new ArrayList<>();
        for (int i = 1; i <= gifts; i++) {
            rows.add(new GiftRow(UUID.randomUUID(), "OS2026-%06d".formatted(i), LocalDate.of(2026, 12, 25),
                    i % 5 == 0 ? "善心人士" : "王小明" + i, "兒童益智積木玩具組", 1, "份", 1200, org, "快樂兒童之家",
                    "祕密暱稱", "秘密願望標題"));
        }
        OrgGroup group = new OrgGroup(org, "快樂兒童之家", "12345678", "秘密地址", "李主任", "王承辦", "0912345678",
                "secret@example.org", false, rows, gifts, gifts * 1200L);
        return new ClosingDataset(c, c.getCollectionStart(), c.getUseEnd(), rows, List.of(group), List.of(),
                new Totals(gifts, gifts, gifts * 1200L, 1), List.of(), List.of());
    }

    private static String text(List<PackageFile> files, String suffix) {
        return files.stream().filter(f -> f.path().endsWith(suffix)).findFirst()
                .map(f -> new String(f.bytes(), StandardCharsets.UTF_8)).orElseThrow();
    }

    @Test
    @DisplayName("圖片寬 2160px 的 JPG，檔名 001 起：摘要、捐贈明細、財物使用情形")
    void imagesAreWideJpegs() throws Exception {
        var built = new FacebookPackageBuilder(GLYPHS, PROPS).build(dataset(60));

        List<PackageFile> images = built.files().stream().filter(f -> f.path().endsWith(".jpg")).toList();
        assertThat(images).hasSizeGreaterThanOrEqualTo(4);                   // 摘要 + 至少兩頁明細 + 財物使用
        assertThat(images.get(0).path()).isEqualTo("02_交給康乃心/Facebook公開徵信/02_徵信圖片/001.jpg");
        BufferedImage first = ImageIO.read(new ByteArrayInputStream(images.get(0).bytes()));
        assertThat(first.getWidth()).isEqualTo(2160);
        assertThat(first.getHeight()).isEqualTo(2700);
        assertThat(images.get(0).bytes()[0]).isEqualTo((byte) 0xFF);        // JPEG 檔頭
        Path dir = Path.of("target", "closing-preview");
        Files.createDirectories(dir);
        Files.write(dir.resolve("fb-1.jpg"), images.get(0).bytes());
        Files.write(dir.resolve("fb-2.jpg"), images.get(1).bytes());
    }

    @Test
    @DisplayName("公開包不含證號、地址、電話、信箱、收據編號、孩子暱稱與願望標題（文字與檔名）")
    void noPrivateData() {
        var built = new FacebookPackageBuilder(GLYPHS, PROPS).build(dataset(12));

        for (PackageFile f : built.files()) {
            assertThat(f.path()).doesNotContain("王小明").doesNotContain("OS2026-0");
            if (f.path().endsWith(".txt")) {
                String content = new String(f.bytes(), StandardCharsets.UTF_8);
                assertThat(content).as(f.path()).doesNotContain("秘密地址").doesNotContain("0912345678")
                        .doesNotContain("secret@example.org").doesNotContain("祕密暱稱").doesNotContain("秘密願望標題")
                        .doesNotContain("A123456789");
            }
        }
        assertThat(built.post().text()).doesNotContain("秘密地址").doesNotContain("祕密暱稱");
    }

    @Test
    @DisplayName("貼文文字：數字填入，現金與利息固定 0，沒有「待核實」；議決情形仍待確認並列入待補")
    void postText() {
        var built = new FacebookPackageBuilder(GLYPHS, PROPS).build(dataset(6));

        String post = text(built.files(), "01_貼文文字.txt");
        assertThat(post).contains("實收禮物6份、物資時價7,200元").contains("現金捐款0元").contains("利息收入0元")
                .contains("本案理事會議決情形：【待確認】").doesNotContain("待核實");
        assertThat(built.todo()).anyMatch(t -> t.missing().contains("議決情形"));
        assertThat(built.todo()).noneMatch(t -> t.missing().contains("尚未核實"));
    }

    @Test
    @DisplayName("回傳給照表操作頁的貼文資訊：全文、圖片張數、則數、第一張圖片的位置")
    void postInfoForOperationPage() {
        var built = new FacebookPackageBuilder(GLYPHS, PROPS).build(dataset(6));

        long images = built.files().stream().filter(f -> f.path().endsWith(".jpg")).count();
        assertThat(built.post().text()).isEqualTo(text(built.files(), "01_貼文文字.txt").replace("﻿", ""));
        assertThat(built.post().imageCount()).isEqualTo((int) images);
        assertThat(built.post().posts()).isEqualTo(1);
        assertThat(built.post().firstImagePath())
                .isEqualTo("02_交給康乃心/Facebook公開徵信/02_徵信圖片/001.jpg");
    }

    @Test
    @DisplayName("圖片太多一則放不下：分成「第01則」「第02則」各含貼文文字與圖片，圖片編號連續")
    void splitsIntoSeveralPosts() {
        var built = new FacebookPackageBuilder(GLYPHS, PROPS, 2).build(dataset(60));

        List<String> paths = built.files().stream().map(PackageFile::path).toList();
        assertThat(paths).anyMatch(p -> p.contains("/第01則/01_貼文文字.txt"));
        assertThat(paths).anyMatch(p -> p.contains("/第02則/02_徵信圖片/003.jpg"));
        assertThat(text(built.files(), "/第02則/01_貼文文字.txt")).contains("第2則／共");
        assertThat(built.post().firstImagePath()).contains("/第01則/02_徵信圖片/001.jpg");
    }
}
