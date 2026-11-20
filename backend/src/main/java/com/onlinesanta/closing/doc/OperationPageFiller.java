package com.onlinesanta.closing.doc;

import static com.onlinesanta.closing.doc.ClosingNarrative.PENDING;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.springframework.stereotype.Component;

import com.onlinesanta.closing.Campaign;
import com.onlinesanta.closing.ClosingProperties;
import com.onlinesanta.closing.doc.ClosingDataset.FinanceMonth;
import com.onlinesanta.closing.doc.ClosingDataset.GiftRow;
import com.onlinesanta.closing.doc.ClosingDataset.MonthRow;
import com.onlinesanta.closing.doc.ClosingDataset.OrgGroup;

/**
 * 預填「照表操作」HTML：把官網欄位的值寫進素材頁，康乃心照頁面逐欄複製到官網。
 *
 * <p>規則（SPEC §5、§12）：
 * <ul>
 *   <li>只動工程端的欄位（{@code data-owner="engineer"}）；協會要填的欄位（公開徵信網址、議決會議）與
 *       審核欄位原樣不動；</li>
 *   <li>系統知道的值寫進去並把 {@code data-ready} 設為 true（頁面的複製按鈕才會啟用）；
 *       不知道的維持「【待確認】」與 {@code data-ready="false"}（複製按鈕停用）——不填 0、不編造；</li>
 *   <li>「說明」文字含任何「【待確認】」就不算備妥；</li>
 *   <li>月份列、財物使用列依資料複製範本列（欄位編號 {@code .0.} 改成 {@code .N.}）。</li>
 * </ul>
 */
@Component
public class OperationPageFiller {

    private final ClosingProperties properties;

    public OperationPageFiller(ClosingProperties properties) {
        this.properties = properties;
    }

    /** 頁面上待補的工程端欄位數（用在待補資料說明）。 */
    public record Filled(byte[] html, int pendingFields) {
    }

    public Filled fill(ClosingDataset d) {
        Document doc = Jsoup.parse(template(d.stage()), "", org.jsoup.parser.Parser.htmlParser());
        doc.outputSettings().prettyPrint(false).escapeMode(Entities.EscapeMode.base).charset(StandardCharsets.UTF_8);

        String p = prefix(d.stage());
        Numbers n = numbers(d);

        // 說明
        List<String> narrative = ClosingNarrative.explain(d);
        set(doc, p + ".description", String.join("\n\n", narrative), !ClosingNarrative.hasPending(narrative));

        // 所得與利息
        set(doc, p + ".income", n.income);
        set(doc, p + ".interest", n.interest);

        // 每月
        List<MonthLine> months = monthLines(d);
        repeat(doc, "monthly_rows", months.size(), (i, row) -> {
            MonthLine m = months.get(i);
            setIn(row, p + ".monthly." + i + ".year", String.valueOf(m.year - 1911), true);
            setIn(row, p + ".monthly." + i + ".month", String.valueOf(m.month), true);
            setIn(row, p + ".monthly." + i + ".income", n(m.income));
            setIn(row, p + ".monthly." + i + ".interest", n(m.interest));
            setIn(row, p + ".monthly." + i + ".expense", n(m.expense));
            setIn(row, p + ".monthly." + i + ".note", m.note, true);
        });

        // 財物使用情形：每個受贈機構一列
        List<OrgGroup> orgs = d.organizations();
        repeat(doc, "expense_items", orgs.size(), (i, row) -> {
            OrgGroup g = orgs.get(i);
            Long self = d.finance().selfFundedAmount() != null && d.finance().selfFundedAmount() == 0 ? Long.valueOf(0) : null;
            setIn(row, p + ".items." + i + ".name", "轉贈物資：" + g.name() + (g.association() ? "（本會自身服務對象）" : ""), true);
            setIn(row, p + ".items." + i + ".detail", itemDetail(g), true);
            setIn(row, p + ".items." + i + ".funded", String.valueOf(g.value()), true);
            setIn(row, p + ".items." + i + ".self", n(self));
            setIn(row, p + ".items." + i + ".total", self == null ? null : String.valueOf(g.value() + self), self != null);
        });
        set(doc, p + ".items_total.funded", String.valueOf(d.totals().value()), !orgs.isEmpty());
        Long selfTotal = d.finance().selfFundedAmount() != null && d.finance().selfFundedAmount() == 0 ? Long.valueOf(0) : null;
        set(doc, p + ".items_total.self", n(selfTotal));
        set(doc, p + ".items_total.total", selfTotal == null ? null : String.valueOf(d.totals().value() + selfTotal), selfTotal != null);

        // 合計
        set(doc, p + ".totals.income", n.income);
        set(doc, p + ".totals.interest", n.interest);
        set(doc, p + ".totals.expense", n.expense);
        set(doc, p + ".totals.balance", n.balance);
        set(doc, p + ".totals.self", n(d.finance().selfFundedAmount()));

        // D 與併報：財物所得用途（社會福利事業一列；其他類別是否填 0 或「不適用」待承辦確認，維持待確認）
        if (d.stage().includesExpenses()) {
            set(doc, p + ".purpose_rows.0.funded", n.expense);
            set(doc, p + ".purpose_rows.0.self", n(d.finance().selfFundedAmount()));
            set(doc, p + ".purpose_rows.0.total", n.expense != null && d.finance().selfFundedAmount() != null
                    ? String.valueOf(Long.parseLong(n.expense) + d.finance().selfFundedAmount()) : null);
            set(doc, p + ".purpose_total.funded", n.expense);
            set(doc, p + ".purpose_total.self", n(d.finance().selfFundedAmount()));
            set(doc, p + ".purpose_total.total", n.expense != null && d.finance().selfFundedAmount() != null
                    ? String.valueOf(Long.parseLong(n.expense) + d.finance().selfFundedAmount()) : null);
        }

        fillStatic(doc, d, n);
        fillTabs(doc);

        int pending = doc.select("[data-owner=engineer][data-ready=false]").size();
        return new Filled(("<!doctype html>\n" + doc.outerHtml().replaceFirst("^<!doctype html>\\s*", ""))
                .getBytes(StandardCharsets.UTF_8), pending);
    }

    // ------------------------------------------------------------ 頁面上的固定文字（沒有欄位編號的）

    /**
     * 素材頁有些資訊不是輸入欄位，而是直接寫在頁面文字裡的「【待確認】」（許可文號、起迄日期、期間、
     * 匯入筆數與合計），再加上清冊預覽表。這些也要依檔期與清冊填入；系統不知道的（預募金額）維持待確認。
     */
    private void fillStatic(Document doc, ClosingDataset d, Numbers n) {
        Campaign c = d.campaign();
        String permit = c.getPermitNo() == null || c.getPermitNo().isBlank() ? null : c.getPermitNo().strip();
        String collection = RocDate.slash(c.getCollectionStart()) + " 至 " + RocDate.slash(c.getCollectionEnd());

        // 所得、利息欄位前面的資料截止日標示
        doc.select("span.cutoff").forEach(e -> e.text("截至" + RocDate.slash(d.cutoff())));

        metaAfter(doc, "勸募許可文號", permit);
        metaAfter(doc, "勸募活動起迄日期", collection);
        metaAfter(doc, "預定經費使用期限", RocDate.slash(c.getUseEnd()));
        metaAfter(doc, "實募金額", n.income() == null ? null : "%,d".formatted(Long.parseLong(n.income())));

        for (Element label : doc.select("div.label")) {
            Element row = label.parent();
            Element value = row == null ? null : row.selectFirst(".static");
            if (value == null) {
                continue;
            }
            if (label.text().strip().equals("期間")) {
                value.html("勸募活動期間：" + Entities.escape(collection) + "<br>財物使用期間："
                        + Entities.escape(RocDate.slash(c.getUseStart()) + " 至 " + RocDate.slash(c.getUseEnd())));
            } else if (label.text().strip().equals("許可文號") && permit != null) {
                value.text(permit);
            }
        }

        // 「匯入後應為：N筆；合計：V元」：與上傳的 Excel 一致
        String count = String.valueOf(d.gifts().size());
        String total = "%,d".formatted(d.totals().value());
        for (Element note : doc.select("p.field-note")) {
            String text = note.text();
            if (text.contains("匯入後應為：") && text.contains(PENDING)) {
                note.text(text.replace(PENDING + "筆", count + "筆").replaceFirst("(合計：)" + java.util.regex.Pattern.quote(PENDING) + "元", "$1" + total + "元"));
            }
        }

        // 清冊預覽表：與匯入的 Excel 同樣的列
        fillTable(doc, "捐贈物資名稱", d.gifts().stream().map(g -> List.of(g.receiptNo(), RocDate.slash(g.donatedOn()),
                g.donorName(), OfficialExcel.itemWithUnit(g), String.valueOf(g.quantity()), "%,d".formatted(g.value()),
                properties.purpose(), "")).toList());
        fillTable(doc, "支出類別", d.gifts().stream().map(g -> List.of("財物支出", g.itemName(), RocDate.slash(g.donatedOn()),
                g.receiptNo(), "%s %d%s，轉贈%s".formatted(g.itemName(), g.quantity(), g.unit(), g.organizationName()),
                "%,d".formatted(g.value()), "其他（實物轉贈）", "")).toList());
    }

    /** 「強調」標籤後面的值（{@code <strong>標籤</strong><span>值</span>}）；值是 null 就不動。 */
    private static void metaAfter(Document doc, String label, String value) {
        if (value == null) {
            return;
        }
        for (Element strong : doc.select("strong")) {
            if (strong.ownText().strip().equals(label)) {
                Element next = strong.nextElementSibling();
                if (next != null && next.tagName().equals("span")) {
                    next.text(value);
                }
            }
        }
    }

    /** 表頭含 {@code header} 的表格：沒有資料列才會有「待實績資料」佔位列，有資料就換成實際列。 */
    private static void fillTable(Document doc, String header, List<List<String>> rows) {
        if (rows.isEmpty()) {
            return;
        }
        for (Element th : doc.select("th")) {
            if (!th.text().strip().equals(header)) {
                continue;
            }
            Element table = th.parents().stream().filter(e -> e.tagName().equals("table")).findFirst().orElse(null);
            Element body = table == null ? null : table.selectFirst("tbody");
            if (body == null) {
                continue;
            }
            body.empty();
            for (List<String> values : rows) {
                Element tr = body.appendElement("tr");
                values.forEach(v -> tr.appendElement("td").text(v));
            }
            return;
        }
    }

    /**
     * 頁籤：只有「這個文件包有的階段」可以點。其他階段的資料夾不在本包內，連過去會是空白頁，
     * 改成停用的文字並說明要另外產生。
     */
    private static void fillTabs(Document doc) {
        for (Element a : doc.select("nav.tabs a")) {
            if (a.hasClass("active")) {
                a.attr("href", "#main");
                continue;
            }
            a.replaceWith(new Element("span").addClass("inactive").attr("aria-disabled", "true")
                    .attr("title", "本文件包不含這個階段；請回系統按對應階段的按鈕另外產生").text(a.text()));
        }
    }

    /** 根目錄的入口頁：直接轉到這個階段的照表操作頁。 */
    public static byte[] startPage(ClosingStage stage) {
        String target = "02_交給康乃心/" + stage.folder() + "/00_照表操作.html";
        return ("<!doctype html>\n<html lang=\"zh-Hant\"><head><meta charset=\"utf-8\"><meta http-equiv=\"refresh\" content=\"0;url="
                + target + "\"><title>" + stage.folder() + "</title><script>location.replace(\"" + target
                + "\");</script></head><body><a href=\"" + target + "\">開啟 " + stage.folder() + "</a></body></html>\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------ 數字

    /** 頁面用的合計（字串；null＝待確認）。 */
    private record Numbers(String income, String interest, String expense, String balance) {
    }

    private record MonthLine(int year, int month, Long income, Long interest, Long expense, String note) {
    }

    private Numbers numbers(ClosingDataset d) {
        long goods = d.totals().value();
        var f = d.finance();
        Long income = f.cashIncome() == null ? null : goods + f.cashIncome();
        Long expense = f.cashExpense() == null ? null : goods + f.cashExpense();
        Long balance = income != null && f.interest() != null && expense != null ? income + f.interest() - expense : null;
        return new Numbers(n(income), n(f.interest()), n(expense), n(balance));
    }

    private static List<MonthLine> monthLines(ClosingDataset d) {
        List<MonthLine> lines = new ArrayList<>();
        YearMonth last = YearMonth.from(d.cutoff());
        for (YearMonth m = YearMonth.from(d.from()); !m.isAfter(last); m = m.plusMonths(1)) {
            final YearMonth key = m;
            MonthRow goods = d.months().stream().filter(r -> r.year() == key.getYear() && r.month() == key.getMonthValue())
                    .findFirst().orElse(null);
            FinanceMonth fin = d.finance().months().stream().filter(r -> r.year() == key.getYear() && r.month() == key.getMonthValue())
                    .findFirst().orElse(null);
            long goodsValue = goods == null ? 0 : goods.value();
            Long cash = fin == null ? null : fin.cashIncome();
            Long cashExpense = fin == null ? null : fin.cashExpense();
            String note = fin != null && fin.note() != null ? fin.note()
                    : goods == null ? "本月無實物捐贈" : "實物捐贈%d件（時價%,d元）".formatted(goods.giftCount(), goods.value());
            lines.add(new MonthLine(m.getYear(), m.getMonthValue(), cash == null ? null : goodsValue + cash,
                    fin == null ? null : fin.interest(), cashExpense == null ? null : goodsValue + cashExpense, note));
        }
        return lines;
    }

    private static String itemDetail(OrgGroup g) {
        List<String> numbers = g.gifts().stream().map(GiftRow::receiptNo).toList();
        String range = numbers.size() <= 3 ? String.join("、", numbers) : numbers.get(0) + " 至 " + numbers.get(numbers.size() - 1);
        return "實物轉贈%d件禮物、共%d份，收據編號%s".formatted(g.gifts().size(), g.quantity(), range);
    }

    private static String n(Long value) {
        return value == null ? null : String.valueOf(value);
    }

    // ------------------------------------------------------------ 寫入欄位

    private static void set(Document doc, String id, String value) {
        set(doc, id, value, value != null);
    }

    private static void set(Document doc, String id, String value, boolean ready) {
        Element el = doc.getElementById(id);
        if (el != null) {
            write(el, value, ready);
        }
    }

    private static void setIn(Element row, String id, String value) {
        setIn(row, id, value, value != null);
    }

    private static void setIn(Element row, String id, String value, boolean ready) {
        Element el = row.getElementById(id);
        if (el != null) {
            write(el, value, ready);
        }
    }

    private static void write(Element el, String value, boolean ready) {
        String text = value == null ? PENDING : value;
        boolean isReady = ready && value != null && !text.contains(PENDING);
        if (el.tagName().equals("textarea")) {
            el.text(text);
        } else {
            el.attr("value", text);
        }
        el.attr("data-ready", String.valueOf(isReady));
        if (isReady) {
            // 備妥後標籤不再帶「待確認」前綴
            for (String attr : new String[] {"data-label", "aria-label"}) {
                if (el.hasAttr(attr)) {
                    el.attr(attr, el.attr(attr).replace(PENDING + " ", ""));
                }
            }
        }
    }

    /** 把範本列（欄位編號 .0.）複製成 count 列並逐列填寫；沒有資料就保留範本列（維持待確認）。 */
    private static void repeat(Document doc, String name, int count, java.util.function.BiConsumer<Integer, Element> filler) {
        Element body = doc.selectFirst("tbody[data-repeat=" + name + "]");
        if (body == null || count == 0) {
            return;
        }
        Element template = body.child(0);
        List<Element> rows = new ArrayList<>();
        rows.add(template);
        for (int i = 1; i < count; i++) {
            Element copy = template.clone();
            for (Element e : copy.getAllElements()) {
                for (String attr : new String[] {"id", "data-field", "data-copy-target"}) {
                    if (e.hasAttr(attr)) {
                        e.attr(attr, e.attr(attr).replace(".0.", "." + i + "."));
                    }
                }
            }
            body.appendChild(copy);
            rows.add(copy);
        }
        for (int i = 0; i < rows.size(); i++) {
            filler.accept(i, rows.get(i));
        }
    }

    static String prefix(ClosingStage stage) {
        return switch (stage) {
            case C -> "c";
            case D -> "d";
            case CD -> "cd";
        };
    }

    private static String template(ClosingStage stage) {
        String name = "closing/templates/operation-" + prefix(stage) + ".html";
        try (InputStream in = OperationPageFiller.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("找不到照表操作範本：" + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
