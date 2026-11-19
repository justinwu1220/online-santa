package com.onlinesanta.closing;

/** 收據拆聯後的兩個檔案。三聯單：紅聯給捐贈人，白聯與黃聯由協會留存。 */
public enum PdfKind {
    /** 紅聯（第三聯，一頁）：給捐贈人的收據。 */
    RED,
    /** 白聯＋黃聯（兩頁，白在前、黃在後）：協會留存，不寄給捐贈人。 */
    WHITE_YELLOW
}
