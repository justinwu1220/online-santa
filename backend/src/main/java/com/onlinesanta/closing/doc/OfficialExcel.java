package com.onlinesanta.closing.doc;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.onlinesanta.closing.ClosingProperties;
import com.onlinesanta.closing.doc.ClosingDataset.GiftRow;
import com.onlinesanta.closing.doc.XlsxTemplateFiller.Cell;

/**
 * 兩份主管機關官方匯入用 Excel：捐物清冊與支出明細。
 *
 * <p>欄位與規則照 SPEC §6：收據編號與日期是文字（日期為民國 {@code YYY/MM/dd}）、數量與金額是數值、
 * 時價是「這一列的總時價」不是單價、沒有公式與多餘欄位、官方範例列已移除。
 */
@Component
public class OfficialExcel {

    private static final String DONATION_TEMPLATE = "closing/templates/donation-list-template.xlsx";
    private static final String EXPENSE_TEMPLATE = "closing/templates/expense-list-template.xlsx";

    private final ClosingProperties properties;

    public OfficialExcel(ClosingProperties properties) {
        this.properties = properties;
    }

    /** 捐物清冊：A 收據編號｜B 捐贈日期｜C 捐贈者名稱或姓名｜D 名稱｜E 數量｜F 時價｜G 捐贈用途，資料從第 3 列起。 */
    public byte[] donationList(ClosingDataset data) {
        List<List<Cell>> rows = new ArrayList<>();
        for (GiftRow g : data.gifts()) {
            rows.add(List.of(
                    Cell.text(g.receiptNo()),
                    Cell.text(RocDate.slash(g.donatedOn())),
                    Cell.text(g.donorName()),
                    Cell.text(itemWithUnit(g)),
                    Cell.number(g.quantity()),
                    Cell.number(g.value()),
                    Cell.text(properties.purpose())));
        }
        return XlsxTemplateFiller.fill(template(DONATION_TEMPLATE), 3, rows);
    }

    /**
     * 支出明細：A 支出類別｜B 支出項目｜C 支出日期｜D 傳票號碼｜E 摘要｜F 支出金額｜G 備註（支付方式）｜H 其他付款方式，
     * 資料從第 2 列起。物資由捐贈者直接送到受贈機構，所以每張收據一列：支出日期是機構實際收到日，
     * 金額是這次實際轉贈的時價，傳票號碼先用收據編號（主管機關承辦是否接受待確認，列在待補資料）。
     */
    public byte[] expenseList(ClosingDataset data) {
        List<List<Cell>> rows = new ArrayList<>();
        for (GiftRow g : data.gifts()) {
            rows.add(List.of(
                    Cell.text("財物支出"),
                    Cell.text(g.itemName()),
                    Cell.text(RocDate.slash(g.donatedOn())),
                    Cell.text(g.receiptNo()),
                    Cell.text("%s %d%s，轉贈%s".formatted(g.itemName(), g.quantity(), g.unit(), g.organizationName())),
                    Cell.number(g.value()),
                    Cell.text("其他"),
                    Cell.text("實物轉贈")));
        }
        return XlsxTemplateFiller.fill(template(EXPENSE_TEMPLATE), 2, rows);
    }

    /** 官方欄位「名稱」要把單位寫在名稱裡（數量欄只放數字）。 */
    static String itemWithUnit(GiftRow g) {
        return "%s（單位：%s）".formatted(g.itemName(), g.unit());
    }

    private static byte[] template(String resource) {
        try (InputStream in = OfficialExcel.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("找不到 Excel 範本：" + resource);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("讀取 Excel 範本失敗：" + resource, e);
        }
    }
}
