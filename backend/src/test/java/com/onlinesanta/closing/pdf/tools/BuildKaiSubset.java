package com.onlinesanta.closing.pdf.tools;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.apache.fontbox.ttf.CmapLookup;
import org.apache.fontbox.ttf.NamingTable;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TTFSubsetter;
import org.apache.fontbox.ttf.TrueTypeFont;

/**
 * 把全字庫正楷體（TW-Kai.ttf，完整約 36MB）裁成專案內建的字型子集。
 *
 * <p>收錄：Big5 能解碼的全部字（常用字與次常用字約 1.3 萬字，涵蓋人名常見字）、ASCII、全形標點與符號、
 * 注音，再加上額外指定的字（{@code extra.txt}，可省略）。裁掉其他字，是因為整份字型 36MB 放進映像與記憶體都太重。
 *
 * <p>用法：{@code java -cp <classes;test-classes;依賴> ...BuildKaiSubset TW-Kai.ttf 輸出.ttf [extra.txt]}
 * 第四個參數 {@code only}：只收額外字與數字 0–9（收據範本標籤用，約 100KB，整份嵌入 PDF）。
 * 另外把授權與字型名稱記錄印出來，寫 {@code OFL.txt} 時對照。
 */
public final class BuildKaiSubset {

    private BuildKaiSubset() {
    }

    public static void main(String[] args) throws Exception {
        TTFParser parser = new TTFParser();
        try (TrueTypeFont font = parser.parse(new org.apache.pdfbox.io.RandomAccessReadBufferedFile(new File(args[0])))) {
            CmapLookup cmap = font.getUnicodeCmapLookup();
            TreeSet<Integer> wanted = new TreeSet<>();
            boolean only = args.length > 3 && "only".equals(args[3]);   // only：只收 extra 的字與數字（收據標籤用的小字型）
            if (only) {
                for (int c = 0x30; c <= 0x39; c++) {
                    wanted.add(c);
                }
            } else {
                for (int c = 0x20; c < 0x7F; c++) {
                    wanted.add(c);
                }
                for (int[] range : new int[][] {{0x2000, 0x206F}, {0x2100, 0x21FF}, {0x2460, 0x24FF}, {0x2500, 0x25FF}, {0x3000, 0x303F},
                        {0x3100, 0x312F}, {0x31A0, 0x31BF}, {0xFE30, 0xFE4F}, {0xFF00, 0xFFEF}, {0xB7, 0xB7}}) {
                    for (int c = range[0]; c <= range[1]; c++) {
                        wanted.add(c);
                    }
                }
                // Big5 雙位元組全部碼位
                Charset big5 = Charset.forName("Big5");
                for (int hi = 0xA1; hi <= 0xF9; hi++) {
                    for (int lo = 0x40; lo <= 0xFE; lo++) {
                        if (lo > 0x7E && lo < 0xA1) {
                            continue;
                        }
                        String s = new String(new byte[] {(byte) hi, (byte) lo}, big5);
                        if (s.length() == 1 && s.charAt(0) != '�') {
                            wanted.add((int) s.charAt(0));
                        }
                    }
                }
            }
            if (args.length > 2) {
                for (int cp : Files.readString(Path.of(args[2])).codePoints().toArray()) {
                    if (cp > 0x20) {
                        wanted.add(cp);
                    }
                }
            }
            TTFSubsetter subsetter = new TTFSubsetter(font);
            List<Integer> missing = new ArrayList<>();
            int kept = 0;
            for (int cp : wanted) {
                if (cmap.getGlyphId(cp) > 0) {
                    subsetter.add(cp);
                    kept++;
                } else {
                    missing.add(cp);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            subsetter.writeToStream(out);
            Files.write(Path.of(args[1]), out.toByteArray());
            System.out.printf("收錄 %d 字，字型沒有 %d 字，輸出 %,d bytes%n", kept, missing.size(), out.size());
            System.out.println("字型沒有的字（前 40）：" + missing.stream().limit(40).map(Character::toString).toList());
            NamingTable names = font.getNaming();
            if (names != null) {
                names.getNameRecords().stream().filter(r -> r.getNameId() <= 14 && r.getPlatformId() == 3)
                        .forEach(r -> System.out.println("name[" + r.getNameId() + "] " + r.getString()));
            }
        }
    }
}
