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
import com.onlinesanta.closing.doc.ClosingDataset.FinanceMonth;
import com.onlinesanta.closing.doc.ClosingDataset.FinanceSummary;
import com.onlinesanta.closing.doc.ClosingDataset.GiftRow;
import com.onlinesanta.closing.doc.ClosingDataset.MonthRow;
import com.onlinesanta.closing.doc.ClosingDataset.OrgGroup;
import com.onlinesanta.closing.doc.ClosingDataset.Totals;

@DisplayName("照表操作 HTML 預填")
class OperationPageFillerTest {

    private static final OperationPageFiller FILLER = new OperationPageFiller();

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

    private static ClosingDataset dataset(ClosingStage stage, FinanceSummary finance) {
        List<OrgGroup> orgs = List.of(org("快樂之家", 2, 1), org("光明學校", 1, 3));
        Campaign c = campaign();
        return new ClosingDataset(c, stage, c.getCollectionStart(), stage.cutoff(c), List.of(), orgs,
                List.of(new MonthRow(2026, 12, 3, 3600)), new Totals(3, 6, 3600, 2), finance, List.of(), List.of());
    }

    private static FinanceSummary unknown() {
        return new FinanceSummary(null, null, null, null, null, List.of());
    }

    private static FinanceSummary allZero(ClosingStage stage) {
        List<FinanceMonth> months = new ArrayList<>();
        int count = stage == ClosingStage.C ? 3 : 3;
        for (int i = 0; i < count; i++) {
            int m = (10 + i) % 12 + 1;
            months.add(new FinanceMonth(m >= 11 ? 2026 : 2027, m, 0L, 0L, 0L, null));
        }
        return new FinanceSummary(0L, 0L, 0L, 0L, null, months);
    }

    private static Document parse(OperationPageFiller.Filled filled) {
        return Jsoup.parse(new String(filled.html(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("C：財物使用情形每個機構一列（編號改成 .N.），金額填入；現金與利息沒填就維持待確認、複製停用")
    void unknownFinanceStaysPending() {
        OperationPageFiller.Filled filled = FILLER.fill(dataset(ClosingStage.C, unknown()));
        Document doc = parse(filled);

        // 兩個機構 → 兩列，欄位編號、複製按鈕目標一起改
        assertThat(doc.getElementById("c.items.0.name").text()).isEqualTo("轉贈物資：快樂之家");
        assertThat(doc.getElementById("c.items.1.name").text()).isEqualTo("轉贈物資：光明學校");
        assertThat(doc.getElementById("c.items.1.funded").attr("value")).isEqualTo("1200");
        assertThat(doc.getElementById("c.items.1.funded").attr("data-ready")).isEqualTo("true");
        assertThat(doc.select("[data-copy-target=c.items.1.funded]")).hasSize(1);
        assertThat(doc.getElementById("c.items.0.detail").text()).contains("2件禮物").contains("OS2026-000001、OS2026-000002");
        assertThat(doc.getElementById("c.items_total.funded").attr("value")).isEqualTo("3600");

        // 沒核實的：待確認、不備妥（頁面的複製按鈕依 data-ready 與內容停用）
        for (String id : List.of("c.income", "c.interest", "c.totals.income", "c.totals.expense", "c.totals.balance", "c.totals.self")) {
            assertThat(doc.getElementById(id).attr("value")).as(id).isEqualTo("【待確認】");
            assertThat(doc.getElementById(id).attr("data-ready")).as(id).isEqualTo("false");
        }
        assertThat(doc.getElementById("c.description").attr("data-ready")).isEqualTo("false");
        assertThat(doc.getElementById("c.description").text()).contains("實收禮物6份、物資時價3,600元");
        assertThat(filled.pendingFields()).isPositive();
    }

    @Test
    @DisplayName("每月列：勸募期間每個月一列；有實物捐贈的月份填入備註，沒填的現金與利息待確認")
    void monthlyRows() {
        Document doc = parse(FILLER.fill(dataset(ClosingStage.C, unknown())));

        // 2026/11、2026/12、2027/1 三個月
        assertThat(doc.select("input[data-field^=c.monthly.][data-field$=.month]")).hasSize(3);
        assertThat(doc.getElementById("c.monthly.0.year").attr("value")).isEqualTo("115");
        assertThat(doc.getElementById("c.monthly.0.month").attr("value")).isEqualTo("11");
        assertThat(doc.getElementById("c.monthly.1.note").text()).isEqualTo("實物捐贈3件（時價3,600元）");
        assertThat(doc.getElementById("c.monthly.0.note").text()).isEqualTo("本月無實物捐贈");
        assertThat(doc.getElementById("c.monthly.1.income").attr("data-ready")).isEqualTo("false");
    }

    @Test
    @DisplayName("財務資料都核實為 0：所得、支出、賸餘備妥；賸餘＝所得＋利息－支出")
    void knownFinanceIsReady() {
        Document doc = parse(FILLER.fill(dataset(ClosingStage.C, allZero(ClosingStage.C))));

        assertThat(doc.getElementById("c.totals.income").attr("value")).isEqualTo("3600");
        assertThat(doc.getElementById("c.totals.expense").attr("value")).isEqualTo("3600");
        assertThat(doc.getElementById("c.totals.balance").attr("value")).isEqualTo("0");
        assertThat(doc.getElementById("c.totals.balance").attr("data-ready")).isEqualTo("true");
        assertThat(doc.getElementById("c.totals.self").attr("value")).isEqualTo("0");
        assertThat(doc.getElementById("c.income").attr("data-ready")).isEqualTo("true");
        // 備妥後標籤不再帶「待確認」前綴
        assertThat(doc.getElementById("c.income").attr("data-label")).doesNotContain("待確認");
    }

    @Test
    @DisplayName("協會要填的欄位與審核欄位原樣不動；工程端已備妥的目的、活動編號保留")
    void associationFieldsAreUntouched() {
        Document doc = parse(FILLER.fill(dataset(ClosingStage.C, unknown())));

        assertThat(doc.getElementById("c.public_url").attr("value")).isEmpty();
        assertThat(doc.getElementById("c.public_url").attr("data-editable")).isEqualTo("true");
        assertThat(doc.getElementById("c.review.letter").attr("value")).isEqualTo("由主管機關填列");
        assertThat(doc.getElementById("c.purpose").attr("data-ready")).isEqualTo("true");
        assertThat(doc.html()).contains("data-copy-target");
    }

    @Test
    @DisplayName("D 與併報用各自的欄位前綴（d.／cd.）；併報頁的審閱欄位維持素材原樣（d.review.*）")
    void stagePrefixes() {
        Document d = parse(FILLER.fill(dataset(ClosingStage.D, allZero(ClosingStage.D))));
        assertThat(d.getElementById("d.totals.income")).isNotNull();
        assertThat(d.getElementById("d.items.1.name").text()).isEqualTo("轉贈物資：光明學校");
        assertThat(d.getElementById("d.purpose_rows.0.funded").attr("value")).isEqualTo("3600");
        assertThat(d.getElementById("d.board_term").attr("data-editable")).isEqualTo("true");

        Document cd = parse(FILLER.fill(dataset(ClosingStage.CD, allZero(ClosingStage.CD))));
        assertThat(cd.getElementById("cd.totals.income").attr("value")).isEqualTo("3600");
        assertThat(cd.getElementById("cd.items.1.name")).isNotNull();
        assertThat(cd.getElementById("d.review.letter").attr("value")).isEqualTo("由主管機關填列");
    }

    @Test
    @DisplayName("根目錄入口頁轉到該階段的照表操作頁")
    void startPage() {
        String html = new String(OperationPageFiller.startPage(ClosingStage.CD), StandardCharsets.UTF_8);

        assertThat(html).contains("02_交給康乃心/成果結案備查併報/00_照表操作.html");
    }
}
