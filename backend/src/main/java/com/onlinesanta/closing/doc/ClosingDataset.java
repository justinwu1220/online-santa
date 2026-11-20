package com.onlinesanta.closing.doc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.onlinesanta.closing.Campaign;

/**
 * 一個檔期、一個階段的結案資料：所有文件（Excel、核章 PDF、成果報告、05A／B／C、Facebook 公開包、照表操作頁）
 * 都從這份資料產生，才會對得上同一個截止日與同一組數字。
 *
 * <p>以有效收據的快照為準（作廢的不算），所以與收據、收據檢查一致。
 *
 * @param from          範圍起點（檔期勸募開始日）
 * @param cutoff        資料截止日
 * @param gifts         範圍內的捐贈，依收據編號排序
 * @param organizations 依受贈機構分組（協會自身服務對象的機構標 {@code association}）
 * @param months        依實際收到日的月份統計，依時間排序
 * @param outOfRange    有效但收到日不在範圍內的收據（不收進文件），格式「編號（日期）」
 */
public record ClosingDataset(
        Campaign campaign,
        ClosingStage stage,
        LocalDate from,
        LocalDate cutoff,
        List<GiftRow> gifts,
        List<OrgGroup> organizations,
        List<MonthRow> months,
        Totals totals,
        List<ReceiptLogRow> receiptLog,
        List<String> outOfRange) {

    /**
     * @param donorName 收據上的捐贈人名稱（勾選要收據者用他填的抬頭，否則帳號真實姓名，沒有就「善心人士」）；
     *                  公開徵信名冊也用這個名稱
     * @param itemName  物資名稱與規格
     * @param value     這一筆的總時價（元），不是單價
     */
    public record GiftRow(
            UUID claimId,
            String receiptNo,
            LocalDate donatedOn,
            String donorName,
            String itemName,
            int quantity,
            String unit,
            int value,
            UUID organizationId,
            String organizationName,
            String childAlias,
            String wishTitle) {
    }

    public record OrgGroup(
            UUID organizationId,
            String name,
            String taxId,
            String address,
            String responsiblePerson,
            String contactPerson,
            String phone,
            String email,
            boolean association,
            List<GiftRow> gifts,
            int quantity,
            long value) {
    }

    public record MonthRow(int year, int month, int giftCount, long value) {
    }

    public record Totals(int giftCount, int quantity, long value, int organizationCount) {
    }

    /** 收據簿的一列（含作廢的）。作廢的收據保留原號與替代關係，不能因為重開就消失。 */
    public record ReceiptLogRow(String receiptNo, String status, LocalDate issuedOn, LocalDate donatedOn, int value,
                                String voidReason, String replacedByNo) {
    }

    /** 外部機構（不含協會自身服務對象）。 */
    public List<OrgGroup> externalOrganizations() {
        return organizations.stream().filter(o -> !o.association()).toList();
    }

    public OrgGroup associationGroup() {
        return organizations.stream().filter(OrgGroup::association).findFirst().orElse(null);
    }
}
