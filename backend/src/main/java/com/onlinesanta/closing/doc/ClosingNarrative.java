package com.onlinesanta.closing.doc;

import java.util.ArrayList;
import java.util.List;

import com.onlinesanta.closing.Campaign;
import com.onlinesanta.closing.doc.ClosingDataset.OrgGroup;

/**
 * 結案「說明」欄與成果報告的文字（依 SPEC 附的填報文案句型）。
 *
 * <p>系統知道的數字（收到的禮物、時價、機構、轉贈）直接填入；系統不知道的（現金、利息、個人實際領受、
 * 差異、議決、公開徵信）寫「【待確認】」，不填 0、不套用「業經通過」。「【待確認】」存在就代表這段文字還不能當最終版。
 */
public final class ClosingNarrative {

    public static final String PENDING = "【待確認】";

    private ClosingNarrative() {
    }

    /** C／D／併報三種階段的說明文字（段落）。 */
    public static List<String> explain(ClosingDataset d) {
        Campaign c = d.campaign();
        boolean permitted = c.getPermitNo() != null && !c.getPermitNo().isBlank();
        String collection = permitted ? RocDate.full(c.getCollectionStart()) + "至" + RocDate.full(c.getCollectionEnd()) : PENDING;
        String usage = permitted ? RocDate.full(c.getUseStart()) + "至" + RocDate.full(c.getUseEnd()) : PENDING;
        String cutoff = RocDate.full(d.cutoff());
        String transfers = transferSentence(d);

        List<String> paragraphs = new ArrayList<>();
        switch (d.stage()) {
            case C -> {
                paragraphs.add("本活動透過線上願望認領募集身心障礙服務對象所需禮物，由捐贈者直送指定機構，本會統籌受贈與轉贈紀錄。"
                        + "勸募期間為" + collection + "，截至" + cutoff + "實收禮物" + d.totals().quantity() + "份、物資時價"
                        + money(d.totals().value()) + "元，現金捐款" + money(d.finance().cashIncome()) + "元，勸募活動期間利息收入"
                        + money(d.finance().interest()) + "元。");
                paragraphs.add(transfers + "募得財物支用" + money(d.totals().value()) + "元，本會自籌支應"
                        + money(d.finance().selfFundedAmount()) + "元，賸餘財物0元。個人實際領受合計" + PENDING + "人、" + PENDING
                        + "份；未發放、退回或其他差異：" + PENDING + "。");
                paragraphs.add("公開徵信辦理情形：" + PENDING + "。所得、使用及捐贈明細詳本表與對應清冊。");
            }
            case D -> {
                paragraphs.add("本活動財物使用期間為" + usage + "，實際執行完竣日為" + PENDING + "。截至" + cutoff + "，實收禮物"
                        + d.totals().quantity() + "份、物資時價" + money(d.totals().value()) + "元，現金捐款"
                        + money(d.finance().cashIncome()) + "元，總利息收入" + money(d.finance().interest()) + "元；募得財物支用"
                        + money(d.totals().value()) + "元，本會自籌支應" + money(d.finance().selfFundedAmount())
                        + "元，賸餘財物0元。");
                paragraphs.add("實際轉贈外部機構" + externalCount(d) + "間、" + externalQuantity(d) + "份，本會直接發放" + directQuantity(d)
                        + "份；個人實際領受共" + PENDING + "人、" + PENDING + "份。未發放、退回或其他差異及處理結果：" + PENDING
                        + "。機構領受與其後個人發放分別留存紀錄，同一份禮物不重複列支。");
                paragraphs.add("本案議決情形：" + PENDING + "；公開徵信辦理情形：" + PENDING + "。實際效益與執行內容詳執行(服務)成果報告，財物支用詳支出明細資料。");
            }
            case CD -> {
                paragraphs.add("本活動透過線上願望認領募集身心障礙服務對象所需禮物。勸募期間為" + collection + "，財物使用期間為" + usage
                        + "，實際執行完竣日為" + PENDING + "；本次辦理成果結案備查併報。");
                paragraphs.add("截至" + cutoff + "，實收禮物" + d.totals().quantity() + "份、物資時價" + money(d.totals().value())
                        + "元，現金捐款" + money(d.finance().cashIncome()) + "元，總利息收入" + money(d.finance().interest())
                        + "元；募得財物支用" + money(d.totals().value()) + "元，本會自籌支應" + money(d.finance().selfFundedAmount())
                        + "元，賸餘財物0元。已轉贈外部機構" + externalCount(d) + "間、" + externalQuantity(d) + "份，本會直接發放"
                        + directQuantity(d) + "份；個人實際領受" + PENDING + "人、" + PENDING + "份。未發放、退回或其他差異及處理結果："
                        + PENDING + "。");
                paragraphs.add("本案議決情形：" + PENDING + "；公開徵信辦理情形：" + PENDING
                        + "。所得、收支、捐贈及財物使用明細詳本表與清冊，實際服務成果詳執行(服務)成果報告。");
            }
        }
        return paragraphs;
    }

    /** 這份文字是否還有待確認的內容。 */
    public static boolean hasPending(List<String> paragraphs) {
        return paragraphs.stream().anyMatch(p -> p.contains(PENDING));
    }

    private static String transferSentence(ClosingDataset d) {
        return "已轉贈外部機構" + externalCount(d) + "間、" + externalQuantity(d) + "份，另由本會直接發放" + directQuantity(d) + "份；";
    }

    static int externalCount(ClosingDataset d) {
        return d.externalOrganizations().size();
    }

    static int externalQuantity(ClosingDataset d) {
        return d.externalOrganizations().stream().mapToInt(OrgGroup::quantity).sum();
    }

    /** 本會直接發放（協會自身服務對象）。沒有指定協會自身服務對象的機構時，系統不知道，寫待確認。 */
    static String directQuantity(ClosingDataset d) {
        if (d.campaign().getAssociationOrgId() == null) {
            return PENDING;
        }
        OrgGroup g = d.associationGroup();
        return String.valueOf(g == null ? 0 : g.quantity());
    }

    /** 金額：null＝待補。 */
    public static String money(Long value) {
        return value == null ? PENDING : String.format("%,d", value);
    }

    public static String money(long value) {
        return String.format("%,d", value);
    }
}
