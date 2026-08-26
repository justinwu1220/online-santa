package com.onlinesanta.wish;

/**
 * 孩童年齡「區間」。
 *
 * <p>刻意不存確切年齡或生日：區間足以讓捐贈者挑選合適的禮物，卻大幅降低了
 * 反推特定孩童身分的可能。這是隱私設計的一部分，不是偷懶。
 */
public enum AgeRange {
    AGE_0_3("0-3 歲"),
    AGE_4_6("4-6 歲"),
    AGE_7_9("7-9 歲"),
    AGE_10_12("10-12 歲"),
    AGE_13_15("13-15 歲"),
    AGE_16_18("16-18 歲");

    private final String label;

    AgeRange(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
