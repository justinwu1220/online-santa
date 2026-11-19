package com.onlinesanta.closing.doc;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.onlinesanta.closing.doc.XlsxTemplateFiller.Cell;

@DisplayName("官方 Excel 範本填寫器")
class XlsxTemplateFillerTest {

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = XlsxTemplateFillerTest.class.getClassLoader().getResourceAsStream(name)) {
            return in.readAllBytes();
        }
    }

    private static Map<String, byte[]> unzip(byte[] zip) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                entries.put(e.getName(), in.readAllBytes());
            }
        }
        return entries;
    }

    private static String sheet(byte[] xlsx) throws IOException {
        return new String(unzip(xlsx).get("xl/worksheets/sheet1.xml"), StandardCharsets.UTF_8);
    }

    private static void assertWellFormed(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("捐物清冊：官方範例列被移除、資料從第 3 列起、文字與數值型別正確、合併儲存格保留")
    void donationListIsFilledInPlace() throws Exception {
        byte[] template = resource("closing/templates/donation-list-template.xlsx");
        byte[] filled = XlsxTemplateFiller.fill(template, 3, List.of(
                List.of(Cell.text("OS2026-000001"), Cell.text("115/12/25"), Cell.text("王小明"),
                        Cell.text("積木 & 繪本（單位：份）"), Cell.number(2), Cell.number(1200), Cell.text("本活動服務對象之個人聖誕禮物")),
                List.of(Cell.text("OS2026-000002"), Cell.text("115/12/26"), Cell.text("善心人士"),
                        Cell.text("文具組（單位：份）"), Cell.number(1), Cell.number(500), Cell.text("本活動服務對象之個人聖誕禮物"))));

        String xml = sheet(filled);
        assertWellFormed(xml);
        assertThat(xml).doesNotContain("123321");                       // 官方範例列已移除
        assertThat(xml).contains("<dimension ref=\"A1:G4\"/>");
        assertThat(xml).contains("<mergeCells count=\"5\">");           // 表頭合併儲存格保留
        // 收據編號與日期是文字（內嵌字串），數量與時價是數值
        assertThat(xml).contains("<c r=\"A3\" s=\"1\" t=\"inlineStr\"><is><t xml:space=\"preserve\">OS2026-000001</t></is></c>");
        assertThat(xml).contains("<c r=\"B3\" s=\"1\" t=\"inlineStr\"><is><t xml:space=\"preserve\">115/12/25</t></is></c>");
        assertThat(xml).contains("<c r=\"E3\" s=\"1\"><v>2</v></c>").contains("<c r=\"F3\" s=\"1\"><v>1200</v></c>");
        assertThat(xml).contains("積木 &amp; 繪本");                     // 特殊字元已跳脫
        assertThat(xml).doesNotContain("<f>");                          // 沒有公式
        // 表頭兩列原樣保留
        assertThat(xml).contains("<row r=\"1\"").contains("<row r=\"2\"");
    }

    @Test
    @DisplayName("支出明細：下拉驗證保留、資料從第 2 列起")
    void expenseListKeepsValidations() throws Exception {
        byte[] template = resource("closing/templates/expense-list-template.xlsx");
        byte[] filled = XlsxTemplateFiller.fill(template, 2, List.of(
                List.of(Cell.text("財物支出"), Cell.text("積木"), Cell.text("115/12/25"), Cell.text("OS2026-000001"),
                        Cell.text("積木 2份，轉贈快樂之家"), Cell.number(1200), Cell.text("其他"), Cell.text("實物轉贈"))));

        String xml = sheet(filled);
        assertWellFormed(xml);
        assertThat(xml).doesNotContain("123321");
        assertThat(xml).contains("<dimension ref=\"A1:H2\"/>");
        assertThat(xml).contains("<dataValidations count=\"2\">");
        assertThat(xml).contains("<c r=\"F2\" s=\"2\"><v>1200</v></c>");
        assertThat(xml).contains("財物支出").contains("實物轉贈");
    }

    @Test
    @DisplayName("沒有任何資料：只剩表頭，範例列一樣移除")
    void emptyDataKeepsOnlyTheHeader() throws Exception {
        byte[] filled = XlsxTemplateFiller.fill(resource("closing/templates/donation-list-template.xlsx"), 3, List.of());

        String xml = sheet(filled);
        assertWellFormed(xml);
        assertThat(xml).doesNotContain("123321").doesNotContain("<row r=\"3\"");
        assertThat(xml).contains("<dimension ref=\"A1:G2\"/>");
    }

    @Test
    @DisplayName("範本其他部分一個位元組都沒動（樣式、共用字串、列印設定、文件屬性）")
    void everythingElseIsUntouched() throws Exception {
        byte[] template = resource("closing/templates/donation-list-template.xlsx");
        byte[] filled = XlsxTemplateFiller.fill(template, 3, List.of(List.of(Cell.text("X"))));

        Map<String, byte[]> before = unzip(template);
        Map<String, byte[]> after = unzip(filled);
        assertThat(after.keySet()).containsExactlyElementsOf(before.keySet());
        for (String name : before.keySet()) {
            if (!name.equals("xl/worksheets/sheet1.xml")) {
                assertThat(after.get(name)).as(name).isEqualTo(before.get(name));
            }
        }
    }

    @Test
    @DisplayName("控制字元與非法 XML 字元被移除，不會讓 Excel 開啟時要求修復")
    void illegalXmlCharactersAreStripped() throws Exception {
        byte[] filled = XlsxTemplateFiller.fill(resource("closing/templates/donation-list-template.xlsx"), 3,
                List.of(List.of(Cell.text("A\u0001B\u0008C<D>"))));

        String xml = sheet(filled);
        assertWellFormed(xml);
        assertThat(xml).contains("ABC&lt;D&gt;");
    }

    @Test
    @DisplayName("欄位字母換算")
    void columnLetters() {
        assertThat(XlsxTemplateFiller.columnLetter(1)).isEqualTo("A");
        assertThat(XlsxTemplateFiller.columnLetter(8)).isEqualTo("H");
        assertThat(XlsxTemplateFiller.columnLetter(27)).isEqualTo("AA");
        assertThat(XlsxTemplateFiller.columnIndex("AB")).isEqualTo(28);
    }
}
