package com.onlinesanta.closing.doc;

import static com.onlinesanta.closing.doc.ClosingNarrative.PENDING;
import static com.onlinesanta.closing.doc.ClosingNarrative.money;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.springframework.stereotype.Component;

import com.onlinesanta.closing.Campaign;
import com.onlinesanta.closing.ClosingProperties;
import com.onlinesanta.closing.doc.ClosingDataset.OrgGroup;
import com.onlinesanta.closing.doc.PdfFlow.Align;
import com.onlinesanta.closing.doc.PdfFlow.Col;
import com.onlinesanta.closing.pdf.GlyphOutlines;

/**
 * 執行（服務）成果報告（A4 直式）。段落順序照 SPEC §7：活動與法人 → 目的與對象 → 辦理情形 → 統計 →
 * 財物所得與使用 → 差異與退回 → 公開徵信 → 簽章區。系統不知道的數字寫「【待確認】」，簽章留白。
 */
@Component
public class ReportPdfWriter {

    /** 活動宗旨（SPEC 附的 C、D 共用目的）。 */
    static final String PURPOSE = "透過線上願望認領方式，連結社會大眾與服務身心障礙者的社福機構及學校，募集符合身心障礙兒童、少年及成人"
            + "個別願望與需求之聖誕禮物，傳遞社會關懷並促進公益參與。";
    static final String SCOPE = "服務範圍為服務身心障礙者的社福機構或學校之身心障礙兒童、少年及成人；不擴及全校學生或機構自用設備。";

    private final GlyphOutlines glyphs;
    private final ClosingProperties properties;

    public ReportPdfWriter(GlyphOutlines glyphs, ClosingProperties properties) {
        this.glyphs = glyphs;
        this.properties = properties;
    }

    public record Result(byte[] pdf, String replacedChars, int pages) {
    }

    public Result write(ClosingDataset d) {
        Campaign c = d.campaign();
        String footer = "%s　%s　資料截止日 %s".formatted(c.getName(), d.stage().label(), RocDate.slash(d.cutoff()));
        try (PdfFlow f = new PdfFlow(glyphs, PDRectangle.A4, 50f, footer)) {
            f.centered("執行（服務）成果報告", 20);
            f.centered("（" + d.stage().label() + "）", 12);
            f.space(8);

            f.heading("一、活動與勸募團體", 13);
            String name = c.getApprovedName() != null ? c.getApprovedName() : c.getName();
            f.field("勸募團體", properties.organizer(), 78, 11);
            f.field("活動名稱", name, 78, 11);
            f.field("活動編號", orPending(c.getActivityNo()), 78, 11);
            f.field("勸募許可文號", orPending(c.getPermitNo()), 78, 11);
            f.field("勸募期間", RocDate.full(c.getCollectionStart()) + "至" + RocDate.full(c.getCollectionEnd()), 78, 11);
            f.field("財物使用期間", RocDate.full(c.getUseStart()) + "至" + RocDate.full(c.getUseEnd()), 78, 11);
            f.field("資料截止日", RocDate.full(d.cutoff()), 78, 11);
            f.space(8);

            f.heading("二、目的與服務對象", 13);
            f.paragraph(PURPOSE, 11, 17);
            f.paragraph(SCOPE, 11, 17);
            f.space(8);

            f.heading("三、辦理情形", 13);
            for (String paragraph : ClosingNarrative.explain(d)) {
                f.paragraph(paragraph, 11, 17);
                f.space(3);
            }
            f.space(6);

            f.heading("四、統計", 13);
            f.table(List.of(new Col("項目", 3f, Align.LEFT), new Col("數量", 1.4f, Align.RIGHT), new Col("說明", 3.6f, Align.LEFT)),
                    stats(d), 10.5f);
            f.space(10);

            f.heading("五、財物所得與使用", 13);
            f.table(List.of(new Col("項目", 3f, Align.LEFT), new Col("金額（元）", 1.8f, Align.RIGHT), new Col("說明", 3.2f, Align.LEFT)),
                    incomeRows(d), 10.5f);
            f.space(10);

            f.heading("六、受贈機構", 13);
            List<List<String>> orgs = new ArrayList<>();
            for (OrgGroup g : d.organizations()) {
                orgs.add(List.of(g.name() + (g.association() ? "（本會自身服務對象）" : ""), String.valueOf(g.gifts().size()),
                        String.valueOf(g.quantity()), String.format("%,d", g.value())));
            }
            if (orgs.isEmpty()) {
                orgs.add(List.of("（本階段範圍內沒有受贈機構）", "0", "0", "0"));
            }
            f.table(List.of(new Col("機構", 4f, Align.LEFT), new Col("禮物件數", 1.2f, Align.RIGHT),
                    new Col("份數", 1f, Align.RIGHT), new Col("時價（元）", 1.5f, Align.RIGHT)), orgs, 10.5f);
            f.space(10);

            f.heading("七、差異、退回與公開徵信", 13);
            f.paragraph("未發放、退回或其他差異及處理結果：" + PENDING + "。", 11, 17);
            f.paragraph("公開徵信辦理情形：" + PENDING + "（實際公告日期、平台與公開內容範圍由本會填寫；未發布即寫待發布）。", 11, 17);
            f.space(4);
            f.signatureRow(List.of("經手人", "會計", "負責人", "團體用印"), 62, 11);

            return new Result(f.finish(), f.replacedChars(), f.pages());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<List<String>> stats(ClosingDataset d) {
        ClosingDataset.Totals t = d.totals();
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("受贈機構數（外部機構）", String.valueOf(d.externalOrganizations().size()), "收到物資的合作機構"));
        rows.add(List.of("完成的願望（禮物）數", String.valueOf(t.giftCount()), "一個願望一份禮物、一張有效收據"));
        rows.add(List.of("實收禮物份數", String.valueOf(t.quantity()), "機構實際收到的份數"));
        rows.add(List.of("機構領受件數", String.valueOf(t.giftCount()), "機構領受；與個人實際領受分開計算"));
        rows.add(List.of("本會直接發放份數", ClosingNarrative.directQuantity(d), "本會自身服務對象"));
        rows.add(List.of("個人實際領受（去重人數／份數）", PENDING, "依機構回傳的 05A 與 05C 實際紀錄，不能由寄達或認領狀態推定"));
        rows.add(List.of("未發放、尚未證明發放的份數", PENDING, "機構尚未向個人發放者另列追蹤"));
        return rows;
    }

    private List<List<String>> incomeRows(ClosingDataset d) {
        ClosingDataset.FinanceSummary fin = d.finance();
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("物資所得（時價）", ClosingNarrative.money(d.totals().value()), "收到日在勸募開始日至截止日之間的有效收據合計"));
        rows.add(List.of("現金捐款", ClosingNarrative.money(fin.cashIncome()), "手動填寫；未核實不寫 0"));
        rows.add(List.of("利息收入（另列）", ClosingNarrative.money(fin.interest()), "手動填寫；不與所得混算"));
        rows.add(List.of("募得財物支用（轉贈）", ClosingNarrative.money(d.totals().value()), "物資直送機構，所得即支用"));
        rows.add(List.of("募得款實支（現金）", ClosingNarrative.money(fin.cashExpense()), "手動填寫"));
        rows.add(List.of("本會自籌（另列）", ClosingNarrative.money(fin.selfFundedAmount()), "自籌不從募得物資扣除"));
        rows.add(List.of("賸餘財物（物資）", "0", "受贈＝轉贈；若有退回或調整須追溯，不強填"));
        return rows;
    }

    private static String orPending(String value) {
        return value == null || value.isBlank() ? PENDING : value;
    }
}
