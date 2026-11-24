package com.onlinesanta.closing.doc;

import static com.onlinesanta.closing.doc.ClosingNarrative.PENDING;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.springframework.stereotype.Component;

import com.onlinesanta.closing.Campaign;
import com.onlinesanta.closing.ClosingProperties;
import com.onlinesanta.closing.doc.ClosingDataset.GiftRow;
import com.onlinesanta.closing.doc.ClosingDataset.OrgGroup;
import com.onlinesanta.closing.doc.PdfFlow.Align;
import com.onlinesanta.closing.doc.PdfFlow.Col;
import com.onlinesanta.closing.pdf.GlyphOutlines;

/**
 * 合作機構確認表：05A（機構物資領受暨發放確認表）、05B（個人物資領受證明，機構留存）、
 * 05C（康乃心服務對象物資領受確認表）。版面與句型依 SPEC 附的 Word 範本，改用 PDFBox 重繪成 PDF。
 *
 * <p>只預填系統知道的資料（活動、許可、機構、禮物編號、品名、數量、機構受贈日期）。系統不知道、也不收集的
 * （個人姓名、身分證字號、戶籍地址、已發放數量、簽章、日期）一律留白給機構或當事人填寫；機構資料缺的欄位寫「【待確認】」。
 * 因此 05A、05B 不含任何個人資料，可以安全寄給機構。
 */
@Component
public class OrgFormPdfWriter {

    /** 表單頁尾標的是這份 PDF 的產生日期（每次重新產生都會更新）。 */
    private static String issuedOn() {
        return RocDate.slash(java.time.LocalDate.now(com.onlinesanta.common.TaiwanYear.ZONE));
    }

    private final GlyphOutlines glyphs;
    private final ClosingProperties properties;

    public OrgFormPdfWriter(GlyphOutlines glyphs, ClosingProperties properties) {
        this.glyphs = glyphs;
        this.properties = properties;
    }

    /** 一份產出的表單。{@code path} 是壓縮檔內的完整路徑。 */
    public record Form(String path, byte[] pdf, String replacedChars) {
    }

    /** 每個外部機構一份 05A 與一份 05B，協會自身服務對象一份 05C（沒有指定或沒有禮物就不產）。 */
    public List<Form> build(ClosingDataset d) {
        List<Form> forms = new ArrayList<>();
        int seq = 0;
        for (OrgGroup g : d.externalOrganizations()) {
            seq++;
            String dir = "04_機構確認/待簽章/%03d_%s/".formatted(seq, safeName(g.name()));
            forms.add(form05A(d, g, seq, dir + "05A_機構物資領受暨發放確認表.pdf"));
            forms.add(form05B(d, g, dir + "05B_個人物資領受證明_機構留存.pdf"));
        }
        OrgGroup association = d.associationGroup();
        if (association != null) {
            forms.add(form05C(d, association, "04_機構確認/康乃心自身服務對象/05C_康乃心服務對象物資領受確認表.pdf"));
        }
        return forms;
    }

    /** 寄給單一機構的兩份表單（寄信用；內容與壓縮檔內的相同）。找不到機構回 null。 */
    public record OrgForms(byte[] form05A, byte[] form05B) {
    }

    public OrgForms buildFor(ClosingDataset d, java.util.UUID organizationId) {
        int seq = 0;
        for (OrgGroup g : d.externalOrganizations()) {
            seq++;
            if (g.organizationId().equals(organizationId)) {
                return new OrgForms(form05A(d, g, seq, "").pdf(), form05B(d, g, "").pdf());
            }
        }
        return null;
    }

    /** 路徑用的機構簡稱：拿掉路徑不允許的字元，最多 20 字。 */
    static String safeName(String name) {
        String clean = name.replaceAll("[\\\\/:*?\"<>|\\s]", "");
        return clean.length() > 20 ? clean.substring(0, 20) : clean;
    }

    // ------------------------------------------------------------ 05A

    private Form form05A(ClosingDataset d, OrgGroup g, int seq, String path) {
        Campaign c = d.campaign();
        try (PdfFlow f = new PdfFlow(glyphs, new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()), 40f,
                "結案 05A　產生日期 " + issuedOn())) {
            f.centered("受贈機構出具", 10);
            f.centered("機構物資領受暨發放確認表", 18);
            f.centered(activityName(c), 12);
            f.space(6);
            f.paragraph("轉贈單位：" + properties.organizer() + "　　勸募許可文號：" + orPending(c.getPermitNo()), 10.5f, 15);
            f.paragraph("受贈機構全銜：" + g.name() + "　　扣繳編號：" + orPending(g.taxId())
                    + "　　表單編號：%s-05A-%03d".formatted(c.getReceiptPrefix(), seq), 10.5f, 15);
            f.paragraph("負責人（代表人或管理人）：" + orPending(g.responsiblePerson()) + "　　地址：" + orPending(g.address()), 10.5f, 15);
            f.paragraph("聯絡窗口及電話：" + contact(g) + "　　出具日期：＿＿＿＿＿＿　　發放統計截止日：＿＿＿＿＿＿", 10.5f, 15);
            f.space(6);

            List<List<String>> rows = new ArrayList<>();
            for (GiftRow gift : g.gifts()) {
                rows.add(List.of(gift.receiptNo(), gift.itemName(), gift.quantity() + " " + gift.unit(),
                        RocDate.slash(gift.donatedOn()), "", "［　］已留存　［　］部分　［　］尚未", ""));
            }
            f.table(List.of(new Col("禮物編號（同收據編號）", 1.6f, Align.LEFT), new Col("受贈品名與規格", 3f, Align.LEFT),
                    new Col("實收數量含單位", 1.2f, Align.CENTER), new Col("機構受贈日期", 1.2f, Align.CENTER),
                    new Col("已發放數量含單位", 1.3f, Align.CENTER), new Col("個人簽收證明留存情形", 2.2f, Align.LEFT),
                    new Col("未發放\n差異或待補事項", 1.8f, Align.LEFT)), rows, 10f, 26);
            f.space(8);
            f.paragraph("本機構確認受領康乃心轉贈之表列物資，實收品名、數量及日期如上。本表兼作本機構出具之物資領受收據。"
                    + "已發放項目之個人領受證明由本機構取得並保存；未發放或證明待補項目已據實註明。"
                    + "相關個人資料不例行回傳，依法查核時於必要範圍提供證明。", 10.5f, 15);
            f.signatureRow(List.of("機構簽章", "授權簽署人簽章", "簽署日期"), 54, 10.5f);
            f.signatureRow(List.of("康乃心核對人簽章", "核對日期", "補正事項"), 54, 10.5f);
            return new Form(path, f.finish(), f.replacedChars());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------ 05B／05C

    private Form form05B(ClosingDataset d, OrgGroup g, String path) {
        return personalForms(d, g, path, "05B", "合作機構留存", "個人物資領受證明 機構留存",
                "同一機構全部受贈人合併一檔，每人各有領受及簽章頁；完整個人資料由發放機構保存，不例行回傳或公開。",
                List.of("發放及轉贈單位：" + g.name(), "活動主辦及前手轉贈單位：" + properties.organizer()));
    }

    private Form form05C(ClosingDataset d, OrgGroup g, String path) {
        return personalForms(d, g, path, "05C", properties.organizer(), "康乃心服務對象物資領受確認表",
                "康乃心全部受贈人合併一檔，每人各有領受及簽章頁；完整個人資料由康乃心保存，不例行回傳或公開。",
                List.of("發放及轉贈單位：" + properties.organizer()));
    }

    /** 每個願望（每位受贈人）一組頁：受贈人資料留白、實際領受物資預填、領受確認與簽章留白。 */
    private Form personalForms(ClosingDataset d, OrgGroup g, String path, String code, String top, String title,
                               String intro, List<String> unitLines) {
        Campaign c = d.campaign();
        try (PdfFlow f = new PdfFlow(glyphs, PDRectangle.A4, 42f, "結案 " + code + "　產生日期 " + issuedOn())) {
            boolean first = true;
            for (GiftRow gift : g.gifts()) {
                if (!first) {
                    f.newPage();
                }
                first = false;
                f.centered(top, 10);
                f.centered(title, 17);
                f.centered(activityName(c), 11);
                f.space(2);
                f.paragraph(intro, 9.5f, 13);
                for (String line : unitLines) {
                    f.paragraph(line, 10.5f, 14);
                }
                f.paragraph("勸募許可文號：" + orPending(c.getPermitNo()), 10.5f, 14);
                f.paragraph("對應願望：" + (gift.childAlias() == null ? "" : gift.childAlias() + "　")
                        + (gift.wishTitle() == null ? "" : gift.wishTitle()), 10.5f, 14);
                f.space(4);

                f.heading("一　受贈人資料", 11.5f);
                f.table(List.of(new Col("項目", 1.2f, Align.CENTER), new Col("資料", 4f, Align.LEFT)),
                        List.of(List.of("姓名", ""), List.of("身分證統一編號", ""), List.of("戶籍地址", "")), 10.5f, 24);
                f.space(6);

                f.heading("二　實際領受物資", 11.5f);
                f.table(List.of(new Col("禮物編號（同收據編號）", 1.6f, Align.LEFT), new Col("受贈品名與規格", 2.6f, Align.LEFT),
                                new Col("實收數量含單位", 1.1f, Align.CENTER), new Col("個人實際受贈日期", 1.3f, Align.CENTER)),
                        List.of(List.of(gift.receiptNo(), gift.itemName(), gift.quantity() + " " + gift.unit(), "")), 10.5f, 28);
                f.paragraph("日期填本人或有權代理人實際領受日；未領受的項目不簽為已領受。多次領受可另列或另附同編號明細。", 9.5f, 13);
                f.space(4);

                f.heading("三　領受確認", 11.5f);
                f.paragraph("本人或代理人確認上述受贈人已領受表列物資，品名、數量及日期與實際情形一致。", 10.5f, 15);
                f.paragraph("簽收方式：［　］本人簽收　　［　］代理人簽收", 10.5f, 15);
                f.signatureRow(List.of("本人或代理人簽章", "簽署日期"), 42, 10);
                f.signatureRow(List.of("代理人姓名及關係（本人簽收免填）", "代理依據及留存佐證（代理簽收時填寫）"), 34, 10);
                f.paragraph("代理人須具代理權並簽自己的姓名；老師、社工或窗口身分本身不能替代代理權。", 9.5f, 13);
                f.signatureRow(List.of("差異或補充說明"), 34, 10);
                f.signatureRow(List.of("轉贈經手人簽章", "核對人簽章"), 40, 10);
                f.signatureRow(List.of("轉贈日期", "核對日期"), 26, 10);
            }
            return new Form(path, f.finish(), f.replacedChars());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------ 共用

    private static String activityName(Campaign c) {
        return c.getApprovedName() != null ? c.getApprovedName() : c.getName();
    }

    private static String contact(OrgGroup g) {
        String person = g.contactPerson();
        String phone = g.phone();
        if ((person == null || person.isBlank()) && (phone == null || phone.isBlank())) {
            return PENDING;
        }
        return ((person == null ? "" : person) + " " + (phone == null ? "" : phone)).strip();
    }

    private static String orPending(String value) {
        return value == null || value.isBlank() ? PENDING : value;
    }
}
