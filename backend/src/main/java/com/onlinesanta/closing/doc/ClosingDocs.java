package com.onlinesanta.closing.doc;

/**
 * 結案文件的固定名稱。
 *
 * <p>成果備查（C）與結案備查（D）不再分別報送：結案即視為兩者都完成，一律產出併報。
 * 資料截止日固定是檔期的財物使用結束日，文件範圍一律涵蓋捐贈與財物使用。
 */
public final class ClosingDocs {

    /** 文件標題與頁尾用的名稱。 */
    public static final String LABEL = "成果結案備查併報";

    /** 壓縮檔裡交給康乃心的資料夾（底下直接放 C 與 D 兩張表的附件，不再分階段資料夾）。 */
    public static final String FOLDER = "02_交給康乃心/";

    private ClosingDocs() {
    }
}
