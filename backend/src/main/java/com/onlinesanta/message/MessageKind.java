package com.onlinesanta.message;

/** 訊息的種類，對應 {@code messages.kind}。 */
public enum MessageKind {
    /** 認領者與機構在認領內的對話。 */
    USER,
    /** 系統通知（認領、取消、寄出、逾期、自動收回、審核結果…）。只給機構看，計入機構的未讀。 */
    SYSTEM,
    /** 認領當下，機構對認領者的招呼。雙方都看得到，視為已讀。 */
    WELCOME
}
