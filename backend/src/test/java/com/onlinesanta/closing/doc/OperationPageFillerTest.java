package com.onlinesanta.closing.doc;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.onlinesanta.closing.Campaign;
import com.onlinesanta.closing.CampaignStatus;
import com.onlinesanta.closing.doc.ClosingDataset.GiftRow;
import com.onlinesanta.closing.doc.ClosingDataset.MonthRow;
import com.onlinesanta.closing.doc.ClosingDataset.OrgGroup;
import com.onlinesanta.closing.doc.ClosingDataset.Totals;

@DisplayName("照表操作 HTML 預填（單一頁面，C 與 D 兩張表）")
class OperationPageFillerTest {

    private static final OperationPageFiller FILLER = new OperationPageFiller(
            new com.onlinesanta.closing.ClosingProperties(30, "本活動服務對象之個人聖誕禮物", "社團法人彰化縣康乃心關懷協會", 500_000));

    /** 真實的貼文一定含議決情形的【待確認】。 */
    private static final FacebookPost POST = new FacebookPost("【線上聖誕老人 2026　公開徵信】\n．本案理事會議決情形：【待確認】\n",
            3, 1, "02_交給康乃心/Facebook公開徵信/02_徵信圖片/001.jpg");

    private static Campaign campaign() {
        Campaign c = new Campaign("線上聖誕老人 2026", LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25),
                LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25), LocalDate.of(2026, 11, 25),
                LocalDate.of(2027, 1, 20), "OS2026", 1);
        c.update(c.getName(), "115000877", "衛部救字第1150000號", null, c.getCollectionStart(), c.getCollectionEnd(),
                c.getUseStart(), c.getUseEnd(), c.getWishListStart(), c.getWishListEnd(), "OS2026", null, null,
                CampaignStatus.ACTIVE);
        return c;
    }

    private static OrgGroup org(String name, int gifts, int firstNo) {
        UUID id = UUID.randomUUID();
        List<GiftRow> rows = new ArrayList<>();
        for (int i = 0; i < gifts; i++) {
            rows.add(new GiftRow(UUID.randomUUID(), "OS2026-%06d".formatted(firstNo + i), LocalDate.of(2026, 12, 25), "王小明",
                    "積木", 2, "份", 1200, id, name, "小星", "積木"));
        }
        return new OrgGroup(id, name, "12345678", null, null, null, null, null, false, rows, gifts * 2, gifts * 1200L);
    }

    private static ClosingDataset dataset() {
        List<OrgGroup> orgs = List.of(org("快樂之家", 2, 1), org("光明學校", 1, 3));
        Campaign c = campaign();
        return new ClosingDataset(c, c.getCollectionStart(), c.getUseEnd(),
                orgs.stream().flatMap(o -> o.gifts().stream()).toList(), orgs,
                List.of(new MonthRow(2026, 12, 3, 3600)), new Totals(3, 6, 3600, 2), List.of(), List.of());
    }

    private static Document parse(OperationPageFiller.Filled filled) {
        return Jsoup.parse(new String(filled.html(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("C 與 D 兩張表都填：財物使用情形固定兩列，現金、利息、自籌固定 0 且備妥")
    void cashFieldsAreZeroAndReady() throws java.io.IOException {
        OperationPageFiller.Filled filled = FILLER.fill(dataset(), POST);
        Document doc = parse(filled);

        // 產出一份可以直接打開目視的頁面
        java.nio.file.Path dir = java.nio.file.Path.of("target", "closing-preview");
        java.nio.file.Files.createDirectories(dir);
        java.nio.file.Files.write(dir.resolve("照表操作.html"), filled.html());

        for (String p : java.util.List.of("c", "d")) {
            // 固定兩列：聖誕禮物（全部物資時價）與活動必要支出（0）；欄位編號與複製按鈕目標一起改
            assertThat(doc.getElementById(p + ".items.0.name").text()).as(p).isEqualTo("聖誕禮物");
            assertThat(doc.getElementById(p + ".items.0.detail").text()).as(p)
                    .isEqualTo("外部機構領受及康乃心個人領受；同一物資僅計一次");
            assertThat(doc.getElementById(p + ".items.0.funded").attr("value")).as(p).isEqualTo("3600");
            assertThat(doc.getElementById(p + ".items.0.total").attr("value")).as(p).isEqualTo("3600");
            assertThat(doc.getElementById(p + ".items.1.name").text()).as(p).isEqualTo("活動必要支出");
            assertThat(doc.getElementById(p + ".items.1.detail").text()).as(p).isEqualTo("本會自籌郵資及結案列印費");
            assertThat(doc.getElementById(p + ".items.1.funded").attr("value")).as(p).isEqualTo("0");
            assertThat(doc.getElementById(p + ".items.1.self").attr("value")).as(p).isEqualTo("0");
            assertThat(doc.getElementById(p + ".items.1.total").attr("value")).as(p).isEqualTo("0");
            assertThat(doc.getElementById(p + ".items.2.name")).as(p).isNull();      // 只有兩列
            assertThat(doc.select("[data-copy-target=" + p + ".items.1.funded]")).as(p).hasSize(1);
            assertThat(doc.select("[data-field^=" + p + ".items.][data-ready=false]")).as(p).isEmpty();
            assertThat(doc.getElementById(p + ".items_total.funded").attr("value")).as(p).isEqualTo("3600");
            assertThat(doc.getElementById(p + ".items_total.total").attr("value")).as(p).isEqualTo("3600");

            // 本活動沒有現金：所得與支出是物資時價，利息、自籌、賸餘是 0，全部備妥（複製按鈕啟用）
            for (var e : java.util.Map.of(p + ".income", "3600", p + ".interest", "0", p + ".totals.income", "3600",
                    p + ".totals.interest", "0", p + ".totals.expense", "3600", p + ".totals.balance", "0",
                    p + ".totals.self", "0").entrySet()) {
                assertThat(doc.getElementById(e.getKey()).attr("value")).as(e.getKey()).isEqualTo(e.getValue());
                assertThat(doc.getElementById(e.getKey()).attr("data-ready")).as(e.getKey()).isEqualTo("true");
            }
            // 說明含個人領受、議決等尚待確認的事實，不備妥
            assertThat(doc.getElementById(p + ".description").attr("data-ready")).as(p).isEqualTo("false");
            assertThat(doc.getElementById(p + ".description").text()).as(p)
                    .contains("實收禮物6份、物資時價3,600元").contains("現金捐款0元");
        }

        // 待補欄位只剩兩張表的說明各 1 個，加上含議決待確認的 Facebook 貼文
        assertThat(filled.pendingFields()).isEqualTo(3);
    }

    @Test
    @DisplayName("每月實募／實支：頁籤上方一份、兩張表共用；每個月一列，所得與支出是該月物資時價，利息 0")
    void monthlyRows() {
        Document doc = parse(FILLER.fill(dataset(), POST));

        // 2026/11、2026/12、2027/1 三個月
        assertThat(doc.select("input[data-field^=c.monthly.][data-field$=.month]")).hasSize(3);
        assertThat(doc.getElementById("c.monthly.0.year").attr("value")).isEqualTo("115");
        assertThat(doc.getElementById("c.monthly.0.month").attr("value")).isEqualTo("11");
        assertThat(doc.getElementById("c.monthly.1.note").text()).isEqualTo("實物捐贈3件（時價3,600元）");
        assertThat(doc.getElementById("c.monthly.0.note").text()).isEqualTo("本月無實物捐贈");
        assertThat(doc.getElementById("c.monthly.1.income").attr("value")).isEqualTo("3600");
        assertThat(doc.getElementById("c.monthly.0.income").attr("value")).isEqualTo("0");
        assertThat(doc.getElementById("c.monthly.0.interest").attr("value")).isEqualTo("0");
        assertThat(doc.getElementById("c.monthly.1.expense").attr("value")).isEqualTo("3600");
        assertThat(doc.select("[data-field^=c.monthly.][data-ready=false]")).isEmpty();
        // 只有一份，而且在頁籤上面
        assertThat(doc.select("tbody[data-repeat=monthly_rows]")).hasSize(1);
        assertThat(doc.select("[data-field^=d.monthly.]")).isEmpty();
    }

    @Test
    @DisplayName("版面順序：本案資料與每月面板在最上面，接著才是官網主頁籤與兩張表的內容")
    void pageOrder() {
        Document doc = parse(FILLER.fill(dataset(), POST));

        var children = doc.getElementById("main").children().stream()
                .map(e -> e.tagName() + "." + (e.id().isEmpty() ? e.className() : e.id())).toList();
        assertThat(children).containsExactly("div.notice", "div.summary", "div.monthly", "nav.tabs",
                "section.view-c", "section.view-d", "section.view-fb", "footer.footer");
    }

    @Test
    @DisplayName("所得、支出、賸餘備妥；賸餘＝所得＋利息－支出＝0")
    void totalsAreReady() {
        Document doc = parse(FILLER.fill(dataset(), POST));

        assertThat(doc.getElementById("c.totals.income").attr("value")).isEqualTo("3600");
        assertThat(doc.getElementById("d.totals.expense").attr("value")).isEqualTo("3600");
        assertThat(doc.getElementById("d.totals.balance").attr("value")).isEqualTo("0");
        assertThat(doc.getElementById("d.totals.balance").attr("data-ready")).isEqualTo("true");
        assertThat(doc.getElementById("c.totals.self").attr("value")).isEqualTo("0");
        assertThat(doc.getElementById("c.income").attr("data-ready")).isEqualTo("true");
        // 備妥後標籤不再帶「待確認」前綴
        assertThat(doc.getElementById("c.income").attr("data-label")).doesNotContain("待確認");
    }

    @Test
    @DisplayName("協會要填的欄位與審核欄位原樣不動；工程端已備妥的目的保留")
    void associationFieldsAreUntouched() {
        Document doc = parse(FILLER.fill(dataset(), POST));

        assertThat(doc.getElementById("c.public_url").attr("value")).isEmpty();
        assertThat(doc.getElementById("d.public_url").attr("data-editable")).isEqualTo("true");
        assertThat(doc.getElementById("d.board_term").attr("data-editable")).isEqualTo("true");
        assertThat(doc.getElementById("c.review.letter").attr("value")).isEqualTo("由主管機關填列");
        assertThat(doc.getElementById("d.review.letter").attr("value")).isEqualTo("由主管機關填列");
        assertThat(doc.getElementById("c.purpose").attr("data-ready")).isEqualTo("true");
        assertThat(doc.html()).contains("data-copy-target");
    }

    @Test
    @DisplayName("尚未備妥的欄位也能複製，按下去會跳提醒；沒內容的欄位才停用")
    void pendingFieldsAreStillCopyable() {
        String html = new String(FILLER.fill(dataset(), POST).html(), StandardCharsets.UTF_8);

        // 只要有內容就能按，備妥與否只決定樣式與提示文字
        assertThat(html).contains("btn.disabled=!copyable")
                .contains("已複製，但這一欄還有「待確認」")
                .doesNotContain("本欄尚未備妥，不能複製待確認文字");
        // 待確認的欄位仍然標成 pending（提醒色），例如兩張表的說明
        Document doc = parse(FILLER.fill(dataset(), POST));
        assertThat(doc.getElementById("c.description").attr("data-ready")).isEqualTo("false");
    }

    @Test
    @DisplayName("財物所得用途（只有 D.結案備查有）：社會福利事業填物資時價，其他 9 類一律 0，十列都備妥")
    void purposeRows() {
        Document doc = parse(FILLER.fill(dataset(), POST));

        assertThat(doc.getElementById("d.purpose_rows.0.funded").attr("value")).isEqualTo("3600");
        assertThat(doc.getElementById("d.purpose_rows.0.self").attr("value")).isEqualTo("0");
        assertThat(doc.getElementById("d.purpose_total.total").attr("value")).isEqualTo("3600");
        for (int row = 1; row <= 9; row++) {
            for (String col : java.util.List.of("funded", "self", "total")) {
                String id = "d.purpose_rows." + row + "." + col;
                assertThat(doc.getElementById(id).attr("value")).as(id).isEqualTo("0");
                assertThat(doc.getElementById(id).attr("data-ready")).as(id).isEqualTo("true");
            }
        }
        assertThat(doc.getElementById("c.purpose_rows.0.funded")).isNull();
    }

    @Test
    @DisplayName("活動編號兩處都填檔期設定的值；預募金額用設定檔的固定金額")
    void activityNoAndPlannedAmount() {
        Document doc = parse(FILLER.fill(dataset(), POST));

        assertThat(doc.getElementById("c.activity_id").text()).isEqualTo("115000877");
        assertThat(doc.getElementById("d.activity_id").text()).isEqualTo("115000877");
        assertThat(doc.body().text()).doesNotContain("【待確認】115000877");
        assertThat(doc.body().text()).contains("預募金額500,000");
        assertThat(doc.body().text()).contains("活動編號115000877");
    }

    @Test
    @DisplayName("許可文號、起迄日期、期間、實募金額等頁面文字依檔期填入；預募金額系統不知道，維持待確認")
    void staticPageTextIsFilled() {
        Document doc = parse(FILLER.fill(dataset(), POST));
        String text = doc.body().text();

        assertThat(text).contains("勸募許可文號衛部救字第1150000號")
                .contains("勸募活動起迄日期115/11/25 至 116/01/25")
                .contains("預定經費使用期限116/01/25")
                .contains("勸募活動期間：115/11/25 至 116/01/25")
                .contains("財物使用期間：115/11/25 至 116/01/25")
                .contains("預募金額500,000");
        // 許可文號列（「期間」下面那一列）
        assertThat(doc.select("div.label:matchesOwn(^許可文號$)").first().parent().select(".static").text())
                .isEqualTo("衛部救字第1150000號");
    }

    @Test
    @DisplayName("所得與利息欄位前的截止日標示＝財物使用結束日")
    void cutoffLabelsAreFilled() {
        Document doc = parse(FILLER.fill(dataset(), POST));

        assertThat(doc.select("span.cutoff")).isNotEmpty()
                .allSatisfy(e -> assertThat(e.text()).isEqualTo("截至" + RocDate.slash(campaign().getUseEnd())));
    }

    @Test
    @DisplayName("匯入筆數與合計、兩張清冊預覽表都與上傳的 Excel 一致")
    void importNotesAndPreviewTables() {
        Document doc = parse(FILLER.fill(dataset(), POST));

        assertThat(doc.select("p.field-note").text()).contains("匯入後應為：3筆；合計：3,600元。")
                .contains("匯入後應為：3筆；支用合計：3,600元。")
                .doesNotContain("匯入後應為：【待確認】");
        var rows = doc.select("th:matchesOwn(^捐贈物資名稱$)").first().parents().select("table").first().select("tbody tr");
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).select("td").eachText()).startsWith("OS2026-000001", "115/12/25", "王小明", "積木（單位：份）", "2", "1,200");

        var expense = doc.select("th:matchesOwn(^支出類別$)").first().parents().select("table").first().select("tbody tr");
        assertThat(expense.get(0).select("td").eachText()).startsWith("財物支出", "積木", "115/12/25", "OS2026-000001");
        assertThat(expense.get(0).select("td").get(4).text()).isEqualTo("積木 2份，轉贈快樂之家");
    }

    @Test
    @DisplayName("官網主頁籤：只有 C.成果備查與 D.結案備查能按，各自切到自己的內容")
    void onlyCandDAreClickable() {
        Document doc = parse(FILLER.fill(dataset(), POST));

        assertThat(doc.select("nav.tabs a")).isEmpty();
        assertThat(doc.select("nav.tabs span.inactive").eachText())
                .containsExactly("案件流程圖", "A.勸募活動申請表", "B.專戶備查", "E.賸餘財物使用申請", "F.活動歷程與年度查核");
        // 可以按的只有 C 與 D（Facebook 的頁籤鈕是隱藏的，只給程式切換用）
        assertThat(doc.select("nav.tabs button:not([hidden])").eachText()).containsExactly("C.成果備查", "D.結案備查");
        assertThat(doc.getElementById("tab-view-c").attr("aria-selected")).isEqualTo("true");
        assertThat(doc.getElementById("tab-view-d").attr("aria-selected")).isEqualTo("false");
        assertThat(doc.getElementById("view-d").hasAttr("hidden")).isTrue();
        // 本案資料與每月面板在頁籤上方，兩張表共用
        assertThat(doc.getElementById("view-c").select("#monthly")).isEmpty();
        assertThat(doc.select("div.summary")).hasSize(1);
    }

    @Test
    @DisplayName("Facebook 公開徵信：沒有自己的頁籤，從 C 與 D 內容裡的按鈕進入，資料都填好")
    void facebookIsEnteredFromCandD() {
        Document doc = parse(FILLER.fill(dataset(), POST));

        // C 與 D 各有一顆「開啟 Facebook 公開徵信」，按了切到同一頁的公開徵信畫面
        assertThat(doc.getElementById("view-c").select("[data-go-tab=view-fb]")).hasSize(1);
        assertThat(doc.getElementById("view-d").select("[data-go-tab=view-fb]")).hasSize(1);
        assertThat(doc.select("[data-go-tab=view-fb]").eachText()).containsOnly("開啟 Facebook 公開徵信");
        // 公開徵信畫面可以回到兩張表
        assertThat(doc.getElementById("view-fb").select("[data-go-tab=view-c], [data-go-tab=view-d]")).hasSize(2);

        assertThat(doc.getElementById("fb-notice-label").text()).isEqualTo("成果結案備查併報");
        assertThat(doc.getElementById("fb.post_text").text()).contains("公開徵信").contains("議決情形");
        assertThat(doc.getElementById("fb.post_text").attr("data-ready")).isEqualTo("false");
        assertThat(doc.getElementById("fb-image-path").text())
                .isEqualTo("02_交給康乃心/Facebook公開徵信/02_徵信圖片/001.jpg");
        assertThat(doc.getElementById("fb-image-count").text()).contains("本次圖片：3張；貼文：1則");
    }

    @Test
    @DisplayName("貼文沒有待確認時，複製鈕可用")
    void facebookReadyWhenNothingPending() {
        FacebookPost ready = new FacebookPost("【線上聖誕老人 2026　公開徵信】感謝每一位捐贈者。", 2, 1, "x/001.jpg");

        Document doc = parse(FILLER.fill(dataset(), ready));

        assertThat(doc.getElementById("fb.post_text").attr("data-ready")).isEqualTo("true");
    }

    @Test
    @DisplayName("現金捐款的處理方式寫明純物資活動、現金 0 元")
    void cashDonorNote() {
        Document doc = parse(FILLER.fill(dataset(), POST));

        assertThat(doc.select("p.field-note").text())
                .contains("本案為純物資活動，現金捐款0元，無捐款人資料").doesNotContain("待核實後在這裡直接寫出");
    }
}
