package com.onlinesanta.wish;

/** 願望分類，供捐贈者篩選。對應 wishes.category 的 CHECK 約束。 */
public enum WishCategory {
    TOY("玩具"),
    BOOK("書籍"),
    CLOTHING("衣物"),
    SPORTS("運動用品"),
    STATIONERY("文具"),
    ELECTRONICS("電子產品"),
    MUSIC("樂器與音樂"),
    ART("美術用品"),
    DAILY_NECESSITIES("生活用品"),
    OTHER("其他");

    private final String label;

    WishCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
