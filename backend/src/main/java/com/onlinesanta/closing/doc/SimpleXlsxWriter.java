package com.onlinesanta.closing.doc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 沒有官方範本的內部活頁簿（結案登記簿）用的最小 xlsx 寫入器：多個工作表、第一列表頭（粗體、自動換行、凍結）、
 * 文字用內嵌字串、數字存數值、固定欄寬。不產生公式與外部連結，Excel 開啟不會要求修復。
 */
public final class SimpleXlsxWriter {

    private SimpleXlsxWriter() {
    }

    /** 一個工作表。{@code rows} 的格子是 String、Number 或 null（空白）。 */
    public record Sheet(String name, List<String> header, List<Float> widths, List<List<Object>> rows) {
    }

    public static byte[] write(List<Sheet> sheets) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
                put(zip, "[Content_Types].xml", contentTypes(sheets.size()));
                put(zip, "_rels/.rels", XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                        + "</Relationships>");
                put(zip, "xl/workbook.xml", workbook(sheets));
                put(zip, "xl/_rels/workbook.xml.rels", workbookRels(sheets.size()));
                put(zip, "xl/styles.xml", styles());
                for (int i = 0; i < sheets.size(); i++) {
                    put(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", sheet(sheets.get(i)));
                }
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";

    private static void put(ZipOutputStream zip, String name, String xml) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(xml.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String contentTypes(int sheets) {
        StringBuilder sb = new StringBuilder(XML + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        for (int i = 1; i <= sheets; i++) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        return sb.append("</Types>").toString();
    }

    private static String workbook(List<Sheet> sheets) {
        StringBuilder sb = new StringBuilder(XML + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
        for (int i = 0; i < sheets.size(); i++) {
            sb.append("<sheet name=\"").append(XlsxTemplateFiller.escape(sheetName(sheets.get(i).name())))
                    .append("\" sheetId=\"").append(i + 1).append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        return sb.append("</sheets></workbook>").toString();
    }

    /** 工作表名稱最多 31 字，不可含 \ / ? * [ ] :。 */
    static String sheetName(String name) {
        String clean = name.replaceAll("[\\\\/?*\\[\\]:]", "_");
        return clean.length() > 31 ? clean.substring(0, 31) : clean;
    }

    private static String workbookRels(int sheets) {
        StringBuilder sb = new StringBuilder(XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 1; i <= sheets; i++) {
            sb.append("<Relationship Id=\"rId").append(i)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                    .append(i).append(".xml\"/>");
        }
        sb.append("<Relationship Id=\"rId").append(sheets + 1)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        return sb.append("</Relationships>").toString();
    }

    private static String styles() {
        return XML + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"微軟正黑體\"/></font>"
                + "<font><b/><sz val=\"11\"/><name val=\"微軟正黑體\"/></font></fonts>"
                + "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>"
                + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"><alignment vertical=\"top\" wrapText=\"1\"/></xf>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"><alignment vertical=\"center\" wrapText=\"1\"/></xf></cellXfs>"
                + "</styleSheet>";
    }

    private static String sheet(Sheet s) {
        StringBuilder sb = new StringBuilder(XML + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        sb.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>");
        if (!s.widths().isEmpty()) {
            sb.append("<cols>");
            for (int i = 0; i < s.widths().size(); i++) {
                sb.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1).append("\" width=\"")
                        .append(s.widths().get(i)).append("\" customWidth=\"1\"/>");
            }
            sb.append("</cols>");
        }
        sb.append("<sheetData>");
        row(sb, 1, s.header().stream().map(h -> (Object) h).toList(), 1);
        int r = 2;
        for (List<Object> row : s.rows()) {
            row(sb, r++, row, 0);
        }
        return sb.append("</sheetData></worksheet>").toString();
    }

    private static void row(StringBuilder sb, int rowNo, List<Object> cells, int style) {
        sb.append("<row r=\"").append(rowNo).append("\">");
        for (int i = 0; i < cells.size(); i++) {
            Object v = cells.get(i);
            String ref = XlsxTemplateFiller.columnLetter(i + 1) + rowNo;
            if (v instanceof Number n) {
                sb.append("<c r=\"").append(ref).append("\" s=\"").append(style).append("\"><v>").append(n).append("</v></c>");
            } else if (v != null && !v.toString().isEmpty()) {
                sb.append("<c r=\"").append(ref).append("\" s=\"").append(style)
                        .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(XlsxTemplateFiller.escape(v.toString())).append("</t></is></c>");
            }
        }
        sb.append("</row>");
    }
}
