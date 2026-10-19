package com.onlinesanta.wish;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 兒童頭貼分配（SPEC 14.1）。
 *
 * <p>與孩子的名字、性別無關：只有「0–3 歲」從 baby1–4 挑，其他年齡從 boy／girl 共 10 張挑。
 * 建立願望時挑一次並存進 {@code wishes.avatar}；之後只有年齡跨過 0–3 歲這條線才重挑，
 * 列表、詳情頁、我的認領顯示的都是同一張。檔案在前端 {@code public/avatars/}。
 */
public final class WishAvatar {

    static final List<String> BABY = List.of("baby1.jpg", "baby2.jpg", "baby3.png", "baby4.png");
    static final List<String> KID = List.of(
            "boy1.jpg", "boy2.png", "boy3.png", "boy4.png", "boy5.png",
            "girl1.png", "girl2.jpg", "girl3.png", "girl4.jpg", "girl5.jpg");

    private WishAvatar() {
    }

    /** 年齡還沒填（草稿）時先當一般年齡挑一張，填了「0–3 歲」才會重挑（見 {@link #afterEdit}）。 */
    public static String pick(AgeRange ageRange) {
        return pick(ageRange, ThreadLocalRandom.current());
    }

    static String pick(AgeRange ageRange, Random random) {
        List<String> pool = isBaby(ageRange) ? BABY : KID;
        return pool.get(random.nextInt(pool.size()));
    }

    /** 修改願望時：年齡沒有跨過 0–3 歲這條線就保留原本的頭貼。 */
    public static String afterEdit(String current, AgeRange oldAge, AgeRange newAge) {
        return isBaby(oldAge) == isBaby(newAge) ? current : pick(newAge);
    }

    private static boolean isBaby(AgeRange ageRange) {
        return ageRange == AgeRange.AGE_0_3;
    }
}
