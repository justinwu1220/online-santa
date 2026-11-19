package com.onlinesanta.closing.doc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 在官方 Excel 範本上填資料。離線建置沒有 POI，而這兩份範本結構單純（一個工作表、沒有公式），
 * 所以直接改工作表 XML：
 * <ul>
 *   <li>範本其他部分（樣式、合併儲存格、下拉驗證、欄寬、列印設定、文件屬性）一個位元組都不動；</li>
 *   <li>第一個資料列起的範例列（官方範例 123321／王小姐）整個移除，換成真正的資料列；</li>
 *   <li>每個欄位沿用範例列同一欄的樣式；文字用內嵌字串（不動共用字串表），數字存數值，不產生任何公式；</li>
 *   <li>官方要求的日期、收據編號都是文字：呼叫端傳文字格子即可。</li>
 * </ul>
 */
public final class XlsxTemplateFiller {

    private XlsxTemplateFiller() {
    }

    /** 一個儲存格：文字或整數。 */
    public record Cell(String text, Long number) {
        public static Cell text(String text) {
            return new Cell(text, null);
        }

        public static Cell number(long number) {
            return new Cell(null, number);
        }

        public static Cell empty() {
            return new Cell(null, null);
        }
    }

    private static final String SHEET = "xl/worksheets/sheet1.xml";
    private static final Pattern ROW = Pattern.compile("<row\\b[^>]*?(?:/>|>.*?</row>)", Pattern.DOTALL);
    private static final Pattern ROW_NUMBER = Pattern.compile("<row\\b[^>]*?\\br=\"(\\d+)\"");
    private static final Pattern CELL = Pattern.compile("<c\\b([^>]*?)/?>");
    private static final Pattern ATTR_REF = Pattern.compile("\\br=\"([A-Z]+)\\d+\"");
    private static final Pattern ATTR_STYLE = Pattern.compile("\\bs=\"(\\d+)\"");
    private static final Pattern DIMENSION = Pattern.compile("<dimension ref=\"[^\"]*\"/>");

    /**
     * @param firstDataRow 第一個資料列的列號（捐物清冊是 3，支出明細是 2）；這一列（含以後）都是範例，會被取代
     */
    public static byte[] fill(byte[] template, int firstDataRow, List<List<Cell>> rows) {
        try {
            String sheet = null;
            Map<String, byte[]> entries = new LinkedHashMap<>();
            try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(template), StandardCharsets.UTF_8)) {
                for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                    byte[] bytes = in.readAllBytes();
                    if (e.getName().equals(SHEET)) {
                        sheet = new String(bytes, StandardCharsets.UTF_8);
                    }
                    entries.put(e.getName(), bytes);
                }
            }
            if (sheet == null) {
                throw new IllegalStateException("範本裡找不到工作表：" + SHEET);
            }
            entries.put(SHEET, fillSheet(sheet, firstDataRow, rows).getBytes(StandardCharsets.UTF_8));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
                for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                    zip.putNextEntry(new ZipEntry(e.getKey()));
                    zip.write(e.getValue());
                    zip.closeEntry();
                }
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("填寫 Excel 範本失敗", e);
        }
    }

    static String fillSheet(String sheet, int firstDataRow, List<List<Cell>> rows) {
        int dataStart = sheet.indexOf("<sheetData>") + "<sheetData>".length();
        int dataEnd = sheet.indexOf("</sheetData>");
        if (dataStart < "<sheetData>".length() || dataEnd < 0) {
            throw new IllegalStateException("範本的工作表沒有 sheetData");
        }
        String body = sheet.substring(dataStart, dataEnd);

        StringBuilder kept = new StringBuilder();
        Map<String, String> styles = new LinkedHashMap<>();
        int lastColumn = 0;
        Matcher m = ROW.matcher(body);
        while (m.find()) {
            String row = m.group();
            Matcher number = ROW_NUMBER.matcher(row);
            int rowNo = number.find() ? Integer.parseInt(number.group(1)) : 0;
            if (rowNo < firstDataRow) {
                kept.append(row);
                lastColumn = Math.max(lastColumn, lastColumnOf(row));
            } else if (rowNo == firstDataRow) {
                Matcher c = CELL.matcher(row);
                while (c.find()) {
                    Matcher ref = ATTR_REF.matcher(c.group(1));
                    Matcher style = ATTR_STYLE.matcher(c.group(1));
                    if (ref.find()) {
                        styles.put(ref.group(1), style.find() ? style.group(1) : "0");
                    }
                }
                lastColumn = Math.max(lastColumn, lastColumnOf(row));
            }
        }
        // 資料列的欄數：以範例列為準，沒有範例列就取資料列最寬的
        int columns = Math.max(lastColumn, rows.stream().mapToInt(List::size).max().orElse(0));

        StringBuilder out = new StringBuilder(kept);
        int rowNo = firstDataRow;
        for (List<Cell> cells : rows) {
            out.append("<row r=\"").append(rowNo).append("\" spans=\"1:").append(columns).append("\">");
            for (int i = 0; i < cells.size(); i++) {
                String col = columnLetter(i + 1);
                String style = styles.getOrDefault(col, "0");
                Cell cell = cells.get(i);
                String ref = col + rowNo;
                if (cell.number() != null) {
                    out.append("<c r=\"").append(ref).append("\" s=\"").append(style).append("\"><v>")
                            .append(cell.number()).append("</v></c>");
                } else if (cell.text() != null && !cell.text().isEmpty()) {
                    out.append("<c r=\"").append(ref).append("\" s=\"").append(style)
                            .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(escape(cell.text()))
                            .append("</t></is></c>");
                } else {
                    out.append("<c r=\"").append(ref).append("\" s=\"").append(style).append("\"/>");
                }
            }
            out.append("</row>");
            rowNo++;
        }
        int lastRow = Math.max(firstDataRow - 1, rowNo - 1);
        String result = sheet.substring(0, dataStart) + out + sheet.substring(dataEnd);
        return DIMENSION.matcher(result).replaceFirst("<dimension ref=\"A1:" + columnLetter(columns) + lastRow + "\"/>");
    }

    private static int lastColumnOf(String row) {
        Matcher c = CELL.matcher(row);
        int last = 0;
        while (c.find()) {
            Matcher ref = ATTR_REF.matcher(c.group(1));
            if (ref.find()) {
                last = Math.max(last, columnIndex(ref.group(1)));
            }
        }
        return last;
    }

    static String columnLetter(int index) {
        StringBuilder sb = new StringBuilder();
        for (int n = index; n > 0; n = (n - 1) / 26) {
            sb.insert(0, (char) ('A' + (n - 1) % 26));
        }
        return sb.toString();
    }

    static int columnIndex(String letters) {
        int n = 0;
        for (char ch : letters.toCharArray()) {
            n = n * 26 + (ch - 'A' + 1);
        }
        return n;
    }

    /** XML 跳脫，並拿掉 XML 不允許的控制字元。 */
    static String escape(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == 0x9 || cp == 0xA || cp == 0xD || (cp >= 0x20 && cp <= 0xD7FF) || (cp >= 0xE000 && cp <= 0xFFFD)
                    || (cp >= 0x10000 && cp <= 0x10FFFF)) {
                switch (cp) {
                    case '&' -> sb.append("&amp;");
                    case '<' -> sb.append("&lt;");
                    case '>' -> sb.append("&gt;");
                    case '"' -> sb.append("&quot;");
                    default -> sb.appendCodePoint(cp);
                }
            }
        }
        return sb.toString();
    }
}
