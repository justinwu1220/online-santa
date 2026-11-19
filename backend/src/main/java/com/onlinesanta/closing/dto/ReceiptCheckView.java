package com.onlinesanta.closing.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 檔期的收據檢查結果。檢查只列出差異，不替使用者補齊或改寫已開立的收據。
 *
 * <p>不含捐贈者姓名與證號：這份報告會放進匯出壓縮檔，也會顯示在管理頁。
 *
 * @param completedWishes   歸屬這個檔期（寄出日在勸募期間內）的已完成認領數
 * @param validReceipts     有效收據張數（壓縮檔兩個資料夾各應有這麼多檔，頁數分別 1 與 2）
 * @param totalValue        有效收據的時價合計（元）
 * @param pdfReady          有效收據中 PDF 已就緒的張數
 */
public record ReceiptCheckView(
        UUID campaignId,
        String campaignName,
        Instant checkedAt,
        int completedWishes,
        int validReceipts,
        int voidedReceipts,
        long totalValue,
        int pdfReady,
        int pdfPending,
        int pdfPendingInfo,
        int pdfFailed,
        List<Issue> issues) {

    public enum Severity { ERROR, WARNING }

    /**
     * @param code      問題類型，前端與報告用同一組代碼
     * @param receiptNo 相關收據編號（沒有收據的問題為 null）
     */
    public record Issue(Severity severity, String code, String receiptNo, String message) {
    }

    public boolean passed() {
        return issues.stream().noneMatch(issue -> issue.severity() == Severity.ERROR);
    }

    /** 放進壓縮檔的文字報告。 */
    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append("收據檢查：").append(campaignName).append('\n');
        sb.append("檢查時間：").append(checkedAt).append("\n\n");
        sb.append("歸屬本檔期的已完成認領：").append(completedWishes).append(" 筆\n");
        sb.append("有效收據：").append(validReceipts).append(" 張（作廢 ").append(voidedReceipts).append(" 張）\n");
        sb.append("有效收據時價合計：").append(totalValue).append(" 元\n");
        sb.append("PDF：已就緒 ").append(pdfReady).append("、待產生 ").append(pdfPending)
                .append("、待補許可文號 ").append(pdfPendingInfo).append("、失敗 ").append(pdfFailed).append('\n');
        sb.append("壓縮檔預期：白聯、黃紅聯資料夾各 ").append(pdfReady).append(" 檔；")
                .append("頁數分別為 1 頁與 2 頁\n\n");
        if (issues.isEmpty()) {
            sb.append("沒有發現問題。\n");
        } else {
            sb.append("需要處理 ").append(issues.stream().filter(i -> i.severity() == Severity.ERROR).count())
                    .append(" 項、提醒 ").append(issues.stream().filter(i -> i.severity() == Severity.WARNING).count())
                    .append(" 項：\n");
            for (Issue issue : issues) {
                sb.append(issue.severity() == Severity.ERROR ? "[需處理] " : "[提醒] ")
                        .append(issue.receiptNo() == null ? "" : issue.receiptNo() + "：")
                        .append(issue.message()).append('\n');
            }
        }
        sb.append("\n編號唯一、一個願望只有一張有效收據，由資料庫的唯一索引保證；本報告不重複檢查。\n");
        return sb.toString();
    }
}
