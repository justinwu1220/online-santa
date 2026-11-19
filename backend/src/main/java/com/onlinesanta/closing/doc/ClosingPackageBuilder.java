package com.onlinesanta.closing.doc;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.onlinesanta.closing.Campaign;
import com.onlinesanta.closing.ClosingProperties;
import com.onlinesanta.closing.doc.ClosingDataset.GiftRow;
import com.onlinesanta.closing.doc.PdfTableWriter.Align;
import com.onlinesanta.closing.doc.PdfTableWriter.Column;

/**
 * 組出結案文件包裡「交給康乃心」那一邊的檔案（官方 Excel、核章 PDF、成果報告、05A／B／C…）。
 * 收據資料夾與收據檢查報告由匯出服務另外串流寫入（檔案大、要並行讀取）。
 *
 * <p>檔名與編號照 SPEC §1、§5：C 成果備查是 {@code 02_捐物人資料}；D 結案備查是 {@code 04_支出明細資料}；
 * 併報是 {@code 02_捐物人資料} 與 {@code 06_支出明細資料}。
 */
@Component
public class ClosingPackageBuilder {

    public record PackageFile(String path, byte[] bytes) {
    }

    public record Built(List<PackageFile> files, List<TodoItem> todo) {
    }

    private final OfficialExcel excel;
    private final PdfTableWriter tables;
    private final ClosingProperties properties;

    public ClosingPackageBuilder(OfficialExcel excel, PdfTableWriter tables, ClosingProperties properties) {
        this.excel = excel;
        this.tables = tables;
        this.properties = properties;
    }

    public Built build(ClosingDataset data) {
        ClosingStage stage = data.stage();
        String dir = "02_交給康乃心/" + stage.folder() + "/";
        List<PackageFile> files = new ArrayList<>();
        List<TodoItem> todo = new ArrayList<>();
        Campaign c = data.campaign();

        if (c.getPermitNo() == null || c.getPermitNo().isBlank()) {
            todo.add(new TodoItem("勸募許可文號", "管理員（檔期設定）", "所有文件的抬頭、收據", "先以「待確認」產出"));
        }
        if (stage.includesDonations()) {
            files.add(new PackageFile(dir + "02_捐物人資料.xlsx", excel.donationList(data)));
            PdfTableWriter.Result pdf = tables.render(donationSpec(data));
            files.add(new PackageFile(dir + "02_捐物人資料.pdf", pdf.pdf()));
            charsTodo(todo, pdf, "02_捐物人資料.pdf");
            todo.add(new TodoItem("經核實的現金捐款（有或無）與捐款人資料", "協會財務",
                    "01_捐款人資料.pdf、成果報告的所得", "只有純物資：現金欄維持「待補」，經核實為 0 才填 0"));
        }
        if (stage.includesExpenses()) {
            String name = stage == ClosingStage.D ? "04_支出明細資料" : "06_支出明細資料";
            files.add(new PackageFile(dir + name + ".xlsx", excel.expenseList(data)));
            PdfTableWriter.Result pdf = tables.render(expenseSpec(data));
            files.add(new PackageFile(dir + name + ".pdf", pdf.pdf()));
            charsTodo(todo, pdf, name + ".pdf");
            todo.add(new TodoItem("支出明細的傳票號碼與支付方式是否被主管機關承辦接受",
                    "協會（向承辦確認）", name + ".xlsx／.pdf",
                    "傳票號碼先用收據編號、支付方式「其他」、其他付款方式「實物轉贈」，待承辦確認"));
        }
        if (!data.outOfRange().isEmpty()) {
            todo.add(new TodoItem("有 %d 張有效收據的收到日不在本階段資料範圍（%s 至 %s）內：%s".formatted(
                    data.outOfRange().size(), RocDate.slash(data.from()), RocDate.slash(data.cutoff()),
                    String.join("、", data.outOfRange().stream().limit(10).toList())
                            + (data.outOfRange().size() > 10 ? " 等" : "")),
                    "協會", "本階段所有文件", "這些收據沒有收進本階段文件；之後的階段會收進"));
        }
        return new Built(files, todo);
    }

    /** 待補資料清單（壓縮檔裡的 待補資料.md）。缺什麼、誰提供、影響哪個輸出、目前可以先完成什麼。 */
    public static String todoMarkdown(ClosingDataset d, List<TodoItem> todo) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 待補資料\n\n");
        sb.append("檔期：").append(d.campaign().getName()).append("　階段：").append(d.stage().label())
                .append("　資料截止日：").append(RocDate.slash(d.cutoff())).append("\n\n");
        sb.append("系統沒有、或還需要人確認的資料都列在這裡。文件上這些欄位寫「待補」或「待確認」，簽章與用印一律留白，"
                + "不填 0、不編造。\n\n");
        if (todo.isEmpty()) {
            sb.append("目前沒有待補項目。\n");
            return sb.toString();
        }
        sb.append("| 缺什麼 | 誰提供 | 影響哪個輸出 | 目前可以先完成什麼 |\n|---|---|---|---|\n");
        for (TodoItem t : todo) {
            sb.append("| ").append(cellText(t.missing())).append(" | ").append(cellText(t.owner())).append(" | ")
                    .append(cellText(t.affects())).append(" | ").append(cellText(t.canDoNow())).append(" |\n");
        }
        return sb.toString();
    }

    private static String cellText(String s) {
        return s == null ? "" : s.replace("|", "／").replace("\n", " ");
    }

    private static void charsTodo(List<TodoItem> todo, PdfTableWriter.Result pdf, String file) {
        if (!pdf.replacedChars().isEmpty()) {
            todo.add(new TodoItem("PDF 字型沒有這些字，已畫成「？」：" + pdf.replacedChars(), "工程（補字型）", file,
                    "Excel 檔內文字完整，不受影響"));
        }
    }

    // ------------------------------------------------------------ 核章 PDF 規格

    PdfTableWriter.Spec donationSpec(ClosingDataset d) {
        List<List<String>> rows = new ArrayList<>();
        for (GiftRow g : d.gifts()) {
            rows.add(List.of(g.receiptNo(), RocDate.slash(g.donatedOn()), g.donorName(),
                    OfficialExcel.itemWithUnit(g), String.valueOf(g.quantity()), money(g.value()), properties.purpose()));
        }
        return new PdfTableWriter.Spec("捐物人資料（捐贈清冊）", header(d, d.totals().giftCount()),
                List.of(new Column("收據編號", 1.5f, Align.LEFT), new Column("捐贈日期\n(YYY/MM/dd)", 1.2f, Align.CENTER),
                        new Column("捐贈者名稱或姓名", 1.7f, Align.LEFT), new Column("捐贈物資名稱", 3.1f, Align.LEFT),
                        new Column("數量", 0.7f, Align.RIGHT), new Column("時價（元）", 1.0f, Align.RIGHT),
                        new Column("捐贈用途", 2.2f, Align.LEFT)),
                rows,
                List.of("合計", "", "", "共 %d 筆".formatted(d.totals().giftCount()),
                        String.valueOf(d.totals().quantity()), money(d.totals().value()), ""),
                footer(d));
    }

    PdfTableWriter.Spec expenseSpec(ClosingDataset d) {
        List<List<String>> rows = new ArrayList<>();
        for (GiftRow g : d.gifts()) {
            rows.add(List.of("財物支出", g.itemName(), RocDate.slash(g.donatedOn()), g.receiptNo(),
                    "%s %d%s，轉贈%s".formatted(g.itemName(), g.quantity(), g.unit(), g.organizationName()),
                    money(g.value()), "其他", "實物轉贈"));
        }
        return new PdfTableWriter.Spec("支出明細資料", header(d, d.totals().giftCount()),
                List.of(new Column("支出類別", 1.0f, Align.LEFT), new Column("支出項目", 2.0f, Align.LEFT),
                        new Column("支出日期\n(YYY/MM/dd)", 1.1f, Align.CENTER), new Column("傳票號碼", 1.4f, Align.LEFT),
                        new Column("摘要", 3.3f, Align.LEFT), new Column("支出金額\n(單位：元)", 1.1f, Align.RIGHT),
                        new Column("備註\n(支付方式)", 0.9f, Align.CENTER), new Column("其他付款方式", 1.0f, Align.CENTER)),
                rows,
                List.of("合計", "共 %d 筆".formatted(d.totals().giftCount()), "", "", "", money(d.totals().value()), "", ""),
                footer(d));
    }

    private List<String> header(ClosingDataset d, int count) {
        Campaign c = d.campaign();
        String name = c.getApprovedName() != null ? c.getApprovedName() : c.getName();
        return List.of(
                "勸募團體全銜：" + properties.organizer(),
                "活動名稱：" + name + "　　活動編號：" + orPending(c.getActivityNo()),
                "勸募許可文號：" + orPending(c.getPermitNo()),
                "勸募期間：" + RocDate.full(c.getCollectionStart()) + "至" + RocDate.full(c.getCollectionEnd())
                        + "　　財物使用期間：" + RocDate.full(c.getUseStart()) + "至" + RocDate.full(c.getUseEnd()),
                "資料截止日：" + RocDate.full(d.cutoff()) + "　　共 " + count + " 筆");
    }

    private String footer(ClosingDataset d) {
        return "%s　%s　資料截止日 %s".formatted(shortName(d.campaign()), d.stage().label(), RocDate.slash(d.cutoff()));
    }

    static String shortName(Campaign c) {
        return c.getName();
    }

    static String orPending(String value) {
        return value == null || value.isBlank() ? "【待確認】" : value;
    }

    static String money(long value) {
        return String.format("%,d", value);
    }
}
