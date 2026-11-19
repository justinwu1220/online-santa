package com.onlinesanta.closing.doc;

import java.time.LocalDate;

import com.onlinesanta.closing.Campaign;

/**
 * 結案的階段。
 *
 * <p>C 成果備查只涵蓋勸募期間；D 結案備查累計到財物使用結束日；併報（CD）一次送兩者。
 * 資料範圍是「實際收到日（＝收據的捐贈日期）從檔期勸募開始日到截止日」，截止日之後才收到的不收進文件，
 * 只在收據檢查報告列出。
 */
public enum ClosingStage {
    C("C.成果備查", "成果備查"),
    D("D.結案備查", "結案備查"),
    CD("成果結案備查併報", "成果結案備查併報");

    private final String folder;
    private final String label;

    ClosingStage(String folder, String label) {
        this.folder = folder;
        this.label = label;
    }

    /** 壓縮檔裡「交給康乃心」底下的資料夾名。 */
    public String folder() {
        return folder;
    }

    public String label() {
        return label;
    }

    /** 這個階段的資料截止日：C 是勸募結束日，D 與併報是財物使用結束日。 */
    public LocalDate cutoff(Campaign campaign) {
        return this == C ? campaign.getCollectionEnd() : campaign.getUseEnd();
    }

    public boolean includesDonations() {
        return this == C || this == CD;
    }

    public boolean includesExpenses() {
        return this == D || this == CD;
    }
}
