package com.onlinesanta.leaderboard;

/**
 * 排行榜稱號。只看<strong>累計</strong>送達份數，與名次無關（SPEC 第 4.3 節）。
 *
 * <p>這是成就不是賽季排名：解鎖之後永久持有，不會因為新的一屆活動重新開始。
 *
 * <p>門檻由低到高宣告，{@link #of(int)} 由高往低找第一個達標的，所以新增一級只要
 * 插在正確的位置、填好門檻即可。中文名稱放這裡是給後端自己用（日後的通知信）；
 * 畫面上的名稱與顏色由前端的 {@code src/lib/tiers.ts} 決定，兩邊的 key 必須一致。
 */
public enum Tier {

    /** 第一份禮物送到了 */
    ELF(1, "小精靈"),
    /** 持續送出溫暖的好夥伴 */
    REINDEER(3, "馴鹿"),
    /** 帶著禮物跑遍好幾個機構 */
    CAPTAIN(5, "雪橇隊長"),
    /** 把 10 份禮物送到孩子手上 */
    LEGEND(10, "傳奇聖誕老人");

    private final int min;
    private final String label;

    Tier(int min, String label) {
        this.min = min;
        this.label = label;
    }

    public int min() {
        return min;
    }

    public String label() {
        return label;
    }

    /** 這個份數目前的稱號；還沒送達任何一份時沒有稱號（null）。 */
    public static Tier of(int delivered) {
        Tier current = null;
        for (Tier tier : values()) {
            if (delivered >= tier.min) {
                current = tier;
            }
        }
        return current;
    }

    /** 下一個還沒達到的稱號；已經是最高一級時回 null。 */
    public static Tier next(int delivered) {
        for (Tier tier : values()) {
            if (delivered < tier.min) {
                return tier;
            }
        }
        return null;
    }
}
