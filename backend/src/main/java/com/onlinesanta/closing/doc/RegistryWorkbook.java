package com.onlinesanta.closing.doc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.onlinesanta.closing.Campaign;
import com.onlinesanta.closing.doc.ClosingDataset.FinanceMonth;
import com.onlinesanta.closing.doc.ClosingDataset.GiftRow;
import com.onlinesanta.closing.doc.ClosingDataset.ReceiptLogRow;
import com.onlinesanta.closing.doc.SimpleXlsxWriter.Sheet;

/**
 * 結案登記簿（內部留存，不是官方匯入檔、也不能當公開徵信）：受贈與轉贈、收據、現金與自籌、勾稽、來源索引。
 * 內含完整的捐贈者名稱，只放在留存備份資料夾。不含身分證字號。
 */
@Component
public class RegistryWorkbook {

    private static final String PENDING = "待補";

    public byte[] build(ClosingDataset d) {
        List<Sheet> sheets = new ArrayList<>();
        sheets.add(gifts(d));
        sheets.add(receipts(d));
        sheets.add(cash(d));
        sheets.add(checks(d));
        sheets.add(source(d));
        return SimpleXlsxWriter.write(sheets);
    }

    /** 受贈與轉贈：物資由捐贈者直送機構，機構實際收到日同時是受贈日與轉贈日。 */
    private Sheet gifts(ClosingDataset d) {
        List<List<Object>> rows = new ArrayList<>();
        for (GiftRow g : d.gifts()) {
            rows.add(row(g.receiptNo(), g.itemName(), g.quantity(), g.unit(), g.value(), RocDate.slash(g.donatedOn()),
                    g.organizationName(), RocDate.slash(g.donatedOn()), "尚未取得個人領受回報"));
        }
        return new Sheet("受贈與轉贈", List.of("禮物編號（同收據編號）", "物資名稱與規格", "數量", "單位", "時價（元）", "受贈日（機構實際收到日）",
                "轉贈對象機構", "轉贈日", "個人領受狀態"),
                List.of(22f, 36f, 8f, 8f, 12f, 18f, 28f, 14f, 24f), rows);
    }

    /** 收據簿：含作廢與重開關係。 */
    private Sheet receipts(ClosingDataset d) {
        List<List<Object>> rows = new ArrayList<>();
        for (ReceiptLogRow r : d.receiptLog()) {
            rows.add(row(r.receiptNo(), r.status(), RocDate.slash(r.issuedOn()), RocDate.slash(r.donatedOn()), r.value(),
                    r.voidReason(), r.replacedByNo()));
        }
        return new Sheet("收據", List.of("收據編號", "狀態", "開立日", "捐贈日（收到日）", "時價（元）", "作廢原因", "重開為（新編號）"),
                List.of(22f, 10f, 14f, 16f, 12f, 30f, 22f), rows);
    }

    /** 現金與自籌：空白＝待補，0＝已核實沒有。 */
    private Sheet cash(ClosingDataset d) {
        List<List<Object>> rows = new ArrayList<>();
        for (FinanceMonth m : d.finance().months()) {
            rows.add(row("%d/%02d".formatted(m.year() - 1911, m.month()), orPending(m.cashIncome()), orPending(m.interest()),
                    orPending(m.cashExpense()), m.note()));
        }
        rows.add(row("自籌（整個檔期）", orPending(d.finance().selfFundedAmount()), null, null, d.finance().selfFundedNote()));
        return new Sheet("現金與自籌", List.of("年月（民國）", "現金實募（元）／自籌金額", "利息（元）", "募得款實支（元）", "備註"),
                List.of(18f, 22f, 14f, 18f, 40f), rows);
    }

    private Sheet checks(ClosingDataset d) {
        List<List<Object>> rows = new ArrayList<>();
        long gifts = d.totals().value();
        rows.add(row("範圍內有效收據張數", d.totals().giftCount(), "收據、受贈清冊、支出明細三者張數必須一致"));
        rows.add(row("受贈（物資時價合計）", gifts, "收到日在勸募開始日至截止日之間的有效收據"));
        rows.add(row("轉贈（物資時價合計）", gifts, "物資由捐贈者直送機構，受贈＝轉贈"));
        rows.add(row("剩餘", 0, "沒有留在協會的物資；若有退回或調整，須追溯，不強填"));
        rows.add(row("受贈 − 轉贈 − 剩餘", 0, "必須為 0"));
        rows.add(row("現金實募", orPending(d.finance().cashIncome()), "未填寫的月份不當作 0"));
        rows.add(row("利息", orPending(d.finance().interest()), "另列，不併入所得"));
        rows.add(row("本階段範圍外的有效收據", d.outOfRange().size(), String.join("、", d.outOfRange())));
        return new Sheet("勾稽", List.of("項目", "數值", "說明"), List.of(30f, 18f, 60f), rows);
    }

    private Sheet source(ClosingDataset d) {
        Campaign c = d.campaign();
        List<List<Object>> rows = new ArrayList<>();
        rows.add(row("檔期", c.getName()));
        rows.add(row("檔期識別", c.getId().toString()));
        rows.add(row("活動編號", c.getActivityNo()));
        rows.add(row("勸募許可文號", c.getPermitNo()));
        rows.add(row("階段", d.stage().label()));
        rows.add(row("資料範圍（收到日）", RocDate.slash(d.from()) + " 至 " + RocDate.slash(d.cutoff())));
        rows.add(row("產生時間", Instant.now().toString()));
        rows.add(row("取數條件", "有效收據（作廢者不計）；收到日＝收據的捐贈日期；範圍＝勸募開始日至該階段截止日"));
        rows.add(row("說明", "本檔為內部留存的登記簿，不是官方匯入檔，也不能當公開徵信；簽章、用印與核對由人工完成。"));
        return new Sheet("來源索引", List.of("項目", "內容"), List.of(24f, 80f), rows);
    }

    private static List<Object> row(Object... cells) {
        List<Object> list = new ArrayList<>(cells.length);
        for (Object c : cells) {
            list.add(c);
        }
        return list;
    }

    private static Object orPending(Long value) {
        return value == null ? PENDING : value;
    }
}
