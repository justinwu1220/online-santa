package com.onlinesanta.closing.pdf;

/**
 * 收據內容放不進版面（太長、字型沒有某個字、金額超出欄位）。
 * 依 SPEC：不裁字、不漏列、不默默改版面，列入待處理由人決定。
 */
public class ReceiptLayoutException extends RuntimeException {

    private final String field;

    public ReceiptLayoutException(String field, String message) {
        super(field + "：" + message);
        this.field = field;
    }

    /** 出問題的欄位名稱（中文，直接給管理員看）。 */
    public String field() {
        return field;
    }
}
