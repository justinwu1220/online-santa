package com.onlinesanta.wish;

import java.util.List;

/**
 * 願望內容的快照：審核會看的欄位加上信件照片的 id。兩份快照相等＝內容沒有任何變更。
 *
 * <p>只存會影響審核的內容；頭貼由年齡區間決定，不另外比對。
 */
public record WishSnapshot(
        String title,
        String childAlias,
        String ageRange,
        String description,
        /** 信件照片的附件 id，已排序，順序不影響比較。 */
        List<String> letterIds) {

    public static WishSnapshot of(Wish wish, List<String> letterIds) {
        return new WishSnapshot(
                wish.getTitle(), wish.getChildAlias(),
                wish.getAgeRange() == null ? null : wish.getAgeRange().name(),
                wish.getDescription(), letterIds.stream().sorted().toList());
    }
}
