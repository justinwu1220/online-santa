package com.onlinesanta.wish;

/** 禮物的預估價格區間（新台幣），讓捐贈者依預算挑選。 */
public enum PriceRange {
    UNDER_500("500 元以下"),
    FROM_500_TO_1000("500-1000 元"),
    FROM_1000_TO_2000("1000-2000 元"),
    OVER_2000("2000 元以上");

    private final String label;

    PriceRange(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
