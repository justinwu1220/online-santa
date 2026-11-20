package com.onlinesanta.closing.doc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.onlinesanta.closing.Campaign;
import com.onlinesanta.closing.CampaignStatus;
import com.onlinesanta.closing.doc.ClosingDataset.GiftRow;
import com.onlinesanta.closing.doc.ClosingDataset.OrgGroup;
import com.onlinesanta.closing.doc.ClosingDataset.Totals;

@DisplayName("結案說明文字：知道的數字填入，不知道的寫待確認")
class ClosingNarrativeTest {

    private static Campaign campaign(String permit, UUID associationOrg) {
        Campaign c = new Campaign("線上聖誕老人 2026", LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25),
                LocalDate.of(2026, 11, 25), LocalDate.of(2027, 1, 25), LocalDate.of(2026, 11, 25),
                LocalDate.of(2027, 1, 20), "OS2026", 1);
        c.update(c.getName(), "115000877", permit, null, c.getCollectionStart(), c.getCollectionEnd(), c.getUseStart(),
                c.getUseEnd(), c.getWishListStart(), c.getWishListEnd(), "OS2026", null, associationOrg, CampaignStatus.ACTIVE);
        return c;
    }

    private static GiftRow gift(UUID org, String orgName, int qty, int value) {
        return new GiftRow(UUID.randomUUID(), "OS2026-000001", LocalDate.of(2026, 12, 25), "王小明", "積木", qty, "份", value,
                org, orgName, "小星", "積木");
    }

    private static ClosingDataset dataset(ClosingStage stage, Campaign c, List<OrgGroup> orgs) {
        int qty = orgs.stream().mapToInt(OrgGroup::quantity).sum();
        long value = orgs.stream().mapToLong(OrgGroup::value).sum();
        return new ClosingDataset(c, stage, c.getCollectionStart(), stage.cutoff(c), List.of(), orgs, List.of(),
                new Totals(orgs.size(), qty, value, orgs.size()), List.of(), List.of());
    }

    private static OrgGroup org(UUID id, String name, boolean association, int qty, long value) {
        return new OrgGroup(id, name, "12345678", null, null, null, null, null, association,
                List.of(gift(id, name, qty, (int) value)), qty, value);
    }

    @Test
    @DisplayName("C：數字填入；現金、利息、自籌固定寫 0（本活動沒有現金）；個人領受、差異、公開徵信待確認")
    void cashIsAlwaysZero() {
        UUID org = UUID.randomUUID();
        ClosingDataset d = dataset(ClosingStage.C, campaign("衛部救字第1150000號", null), List.of(org(org, "快樂之家", false, 12, 14400)));

        List<String> text = ClosingNarrative.explain(d);

        String all = String.join("\n", text);
        assertThat(all).contains("實收禮物12份、物資時價14,400元");
        assertThat(all).contains("現金捐款0元").contains("利息收入0元").contains("本會自籌支應0元");
        assertThat(all).contains("已轉贈外部機構1間、12份").contains("募得財物支用14,400元").contains("賸餘財物0元");
        assertThat(all).contains("個人實際領受合計【待確認】人");
        assertThat(all).doesNotContain("現金捐款【待確認】");
        assertThat(ClosingNarrative.hasPending(text)).isTrue();
    }

    @Test
    @DisplayName("D、併報也寫 0；沒有指定協會自身服務對象時，本會直接發放份數待確認")
    void zeroInAllStages() {
        UUID org = UUID.randomUUID();

        ClosingDataset noAssociation = dataset(ClosingStage.CD, campaign("衛部救字第1150000號", null),
                List.of(org(org, "快樂之家", false, 5, 5000)));
        String text = String.join("\n", ClosingNarrative.explain(noAssociation));
        assertThat(text).contains("現金捐款0元").contains("總利息收入0元").contains("本會自籌支應0元");
        assertThat(text).contains("本會直接發放【待確認】份");
        assertThat(String.join("\n", ClosingNarrative.explain(
                dataset(ClosingStage.D, campaign("衛部救字第1150000號", null), List.of(org(org, "快樂之家", false, 5, 5000))))))
                .contains("現金捐款0元").contains("總利息收入0元");

        UUID assoc = UUID.randomUUID();
        ClosingDataset withAssociation = dataset(ClosingStage.CD, campaign("衛部救字第1150000號", assoc),
                List.of(org(org, "快樂之家", false, 5, 5000), org(assoc, "康乃心服務對象", true, 3, 3000)));
        String text2 = String.join("\n", ClosingNarrative.explain(withAssociation));
        assertThat(text2).contains("已轉贈外部機構1間、5份").contains("本會直接發放3份");
    }

    @Test
    @DisplayName("沒有許可文號時，期間寫待確認（申請的期間不能當成核准的期間）")
    void periodsAreNotApprovedWithoutAPermit() {
        ClosingDataset d = dataset(ClosingStage.D, campaign(null, null), List.of());

        String text = String.join("\n", ClosingNarrative.explain(d));

        assertThat(text).contains("財物使用期間為【待確認】");
        assertThat(text).doesNotContain("中華民國115年11月25日至");
    }

    @Test
    @DisplayName("金額格式：千分位、待補")
    void money() {
        assertThat(ClosingNarrative.money(1234567L)).isEqualTo("1,234,567");
        assertThat(ClosingNarrative.money((Long) null)).isEqualTo("【待確認】");
        assertThat(ClosingNarrative.money(0L)).isEqualTo("0");
    }
}
