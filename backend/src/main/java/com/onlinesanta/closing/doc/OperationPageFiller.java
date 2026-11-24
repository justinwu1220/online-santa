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
import com.onlinesanta.closing.doc.ClosingDataset.MonthRow;

/**
 * 預填「照表操作」HTML：把官網欄位的值寫進素材頁，康乃心照頁面逐欄複製到官網。
 *
 * <p>整個結案文件包只有這一個 HTML（壓縮檔根目錄的 {@code 照表操作.html}）。版面照官網：最上面是本案資料
 * 與每月實募／實支面板（兩張表共用），接著是主頁籤，只有 C.成果備查與 D.結案備查可以按，
 * 各自顯示自己那張表的內容；Facebook 公開徵信沒有自己的頁籤，從 C 或 D 內容裡的
 *「開啟 Facebook 公開徵信」按鈕進入。兩張表的數字來自同一次結案。
 *
 * <p>規則（SPEC §5、§12）：
 * <ul>
 *   <li>只動工程端的欄位（{@code data-owner="engineer"}）；協會要填的欄位（公開徵信網址、議決會議）與
 *       審核欄位原樣不動；</li>
 *   <li>活動編號、預募金額這類頁首資訊也一起填（不是輸入框，但官網要照抄）；</li>
 *   <li>系統知道的值寫進去並把 {@code data-ready} 設為 true；不知道的維持「【待確認】」與
 *       {@code data-ready="false"}——不填 0、不編造。待確認的欄位仍然可以複製（官網有些欄位必填），
 *       只是複製鈕是提醒色，按下去會跳出「還有待確認，補齊後記得再複製一次」；空欄位才真的停用；</li>
 *   <li>「說明」文字含任何「【待確認】」就不算備妥；</li>
 *   <li>月份列、財物使用列依資料複製範本列（欄位編號 {@code .0.} 改成 {@code .N.}）。</li>
 * </ul>
 */
@Component
public class OperationPageFiller {

    private static final String TEMPLATE = "closing/templates/operation.html";

    /** 兩張官網表單的欄位編號前綴與它們在頁面上的區塊。 */
    private static final String[][] VIEWS = {{"c", "view-c"}, {"d", "view-d"}};

    private final ClosingProperties properties;

    public OperationPageFiller(ClosingProperties properties) {
        this.properties = properties;
    }

    /** 頁面上待補的工程端欄位數（用在待補資料說明）。 */
    public record Filled(byte[] html, int pendingFields) {
    }

    public Filled fill(ClosingDataset d, FacebookPost facebook) {
        Document doc = Jsoup.parse(template(), "", org.jsoup.parser.Parser.htmlParser());
        doc.outputSettings().prettyPrint(false).escapeMode(Entities.EscapeMode.base).charset(StandardCharsets.UTF_8);

        Numbers n = numbers(d);
        List<String> narrative = ClosingNarrative.explain(d);

        // 頁籤上方的每月實募／實支：兩張表共用一份（素材的兩份只差一句說明），欄位沿用 C 的編號
        List<MonthLine> months = monthLines(d);
        repeat(doc.body(), "monthly_rows", months.size(), (i, row) -> {
            MonthLine m = months.get(i);
            String id = "c.monthly." + i + ".";
            setIn(row, id + "year", String.valueOf(m.year - 1911), true);
            setIn(row, id + "month", String.valueOf(m.month), true);
            setIn(row, id + "income", n(m.income));
            setIn(row, id + "interest", n(m.interest));
            setIn(row, id + "expense", n(m.expense));
            setIn(row, id + "note", m.note, true);
        });

        // C.成果備查與 D.結案備查兩張表都填：數字相同，來自同一次結案
        for (String[] view : VIEWS) {
            String p = view[0];
            Element panel = doc.getElementById(view[1]);
            if (panel == null) {
                continue;
            }
            fillView(doc, panel, p, d, n, narrative);
        }

        fillStatic(doc, d, n);
        fillFacebook(doc, facebook);

        int pending = doc.select("[data-owner=engineer][data-ready=false]").size();
        return new Filled(("<!doctype html>\n" + doc.outerHtml().replaceFirst("^<!doctype html>\\s*", ""))
                .getBytes(StandardCharsets.UTF_8), pending);
    }

    /** 一張表（C 或 D）的所有工程端欄位。欄位不存在於這張表時 {@link #set} 會自動略過。 */
    private void fillView(Document doc, Element panel, String p, ClosingDataset d, Numbers n, List<String> narrative) {
        // 活動編號（檔期設定的必填欄位）
        set(doc, p + ".activity_id", blankToNull(d.campaign().getActivityNo()));

        // 說明
        set(doc, p + ".description", String.join("\n\n", narrative), !ClosingNarrative.hasPending(narrative));

        // 所得與利息
        set(doc, p + ".income", n.income);
        set(doc, p + ".interest", n.interest);

        // 財物使用情形：固定兩列（需求方決定），不按受贈機構拆
        List<ItemLine> items = itemLines(d);
        repeat(panel, "expense_items", items.size(), (i, row) -> {
            ItemLine item = items.get(i);
            setIn(row, p + ".items." + i + ".name", item.name(), true);
            setIn(row, p + ".items." + i + ".detail", item.detail(), true);
            setIn(row, p + ".items." + i + ".funded", item.amount(), true);
            setIn(row, p + ".items." + i + ".self", "0");
            setIn(row, p + ".items." + i + ".total", item.amount(), true);
        });
        set(doc, p + ".items_total.funded", String.valueOf(d.totals().value()), true);
        set(doc, p + ".items_total.self", "0");
        set(doc, p + ".items_total.total", String.valueOf(d.totals().value()), true);

        // 合計
        set(doc, p + ".totals.income", n.income);
        set(doc, p + ".totals.interest", n.interest);
        set(doc, p + ".totals.expense", n.expense);
        set(doc, p + ".totals.balance", n.balance);
        set(doc, p + ".totals.self", "0");

        // 財物所得用途（只有 D.結案備查有這一區）：第 1 列社會福利／社會慈善事業是全部的物資時價，
        // 其他 9 類本活動沒有支用，依需求方決定一律填 0
        set(doc, p + ".purpose_rows.0.funded", n.expense);
        set(doc, p + ".purpose_rows.0.self", "0");
        set(doc, p + ".purpose_rows.0.total", n.expense);
        for (int row = 1; row <= 9; row++) {
            set(doc, p + ".purpose_rows." + row + ".funded", "0");
            set(doc, p + ".purpose_rows." + row + ".self", "0");
            set(doc, p + ".purpose_rows." + row + ".total", "0");
        }
        set(doc, p + ".purpose_total.funded", n.expense);
        set(doc, p + ".purpose_total.self", "0");
        set(doc, p + ".purpose_total.total", n.expense);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
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

        metaAfter(doc, "活動編號", blankToNull(c.getActivityNo()));
        metaAfter(doc, "預募金額", "%,d".formatted(properties.plannedAmount()));
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

        // 本活動沒有現金捐款：捐款人資料不適用
        for (Element note : doc.select("p.field-note")) {
            if (note.text().startsWith("現金捐款及0元資料的本案處理方式：")) {
                note.text("現金捐款及0元資料的本案處理方式：本案為純物資活動，現金捐款0元，無捐款人資料，不另上傳捐款人資料。");
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

    /** Facebook 公開徵信頁籤：貼文全文、圖片張數與則數、圖片在壓縮檔裡的位置。 */
    private static void fillFacebook(Document doc, FacebookPost fb) {
        if (fb == null) {
            return;
        }
        Element label = doc.getElementById("fb-notice-label");
        if (label != null) {
            label.text(ClosingDocs.LABEL);
        }
        Element text = doc.getElementById("fb.post_text");
        if (text != null) {
            write(text, fb.text(), !fb.text().contains(PENDING));
        }
        Element path = doc.getElementById("fb-image-path");
        if (path != null) {
            path.text(fb.firstImagePath());
        }
        Element count = doc.getElementById("fb-image-count");
        if (count != null) {
            count.text("本次圖片：%d張；貼文：%d則。若需多則，系統已經先分包，不用自己拆圖。"
                    .formatted(fb.imageCount(), fb.posts()));
        }
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

    // ------------------------------------------------------------ 數字

    /** 頁面用的合計（字串；null＝待確認）。 */
    private record Numbers(String income, String interest, String expense, String balance) {
    }

    private record MonthLine(int year, int month, Long income, Long interest, Long expense, String note) {
    }

    /** 財物使用情形的一列。 */
    private record ItemLine(String name, String detail, String amount) {
    }

    /**
     * 財物使用情形固定兩列：募得的物資全部算「聖誕禮物」（外部機構與康乃心自己的服務對象都在裡面，
     * 同一份物資只計一次），另外列一筆「活動必要支出」，本活動全部是 0。
     */
    private static List<ItemLine> itemLines(ClosingDataset d) {
        return List.of(
                new ItemLine("聖誕禮物", "外部機構領受及康乃心個人領受；同一物資僅計一次", String.valueOf(d.totals().value())),
                new ItemLine("活動必要支出", "本會自籌郵資及結案列印費", "0"));
    }

    /** 本活動沒有現金捐款、利息與自籌：所得與支出都是物資時價，利息、賸餘為 0。 */
    private Numbers numbers(ClosingDataset d) {
        String goods = String.valueOf(d.totals().value());
        return new Numbers(goods, "0", goods, "0");
    }

    private static List<MonthLine> monthLines(ClosingDataset d) {
        List<MonthLine> lines = new ArrayList<>();
        YearMonth last = YearMonth.from(d.cutoff());
        for (YearMonth m = YearMonth.from(d.from()); !m.isAfter(last); m = m.plusMonths(1)) {
            final YearMonth key = m;
            MonthRow goods = d.months().stream().filter(r -> r.year() == key.getYear() && r.month() == key.getMonthValue())
                    .findFirst().orElse(null);
            long goodsValue = goods == null ? 0 : goods.value();
            String note = goods == null ? "本月無實物捐贈" : "實物捐贈%d件（時價%,d元）".formatted(goods.giftCount(), goods.value());
            lines.add(new MonthLine(m.getYear(), m.getMonthValue(), goodsValue, 0L, goodsValue, note));
        }
        return lines;
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
        if (el.tagName().equals("input")) {
            el.attr("value", text);
        } else {
            el.text(text);                      // textarea 與範本裡的 span（活動編號、檔名）
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
    private static void repeat(Element panel, String name, int count, java.util.function.BiConsumer<Integer, Element> filler) {
        Element body = panel.selectFirst("tbody[data-repeat=" + name + "]");
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

    private static String template() {
        try (InputStream in = OperationPageFiller.class.getClassLoader().getResourceAsStream(TEMPLATE)) {
            if (in == null) {
                throw new IllegalStateException("找不到照表操作範本：" + TEMPLATE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
