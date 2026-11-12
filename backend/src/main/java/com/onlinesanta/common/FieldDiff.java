package com.onlinesanta.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.onlinesanta.common.dto.FieldChange;

/**
 * 兩份欄位快照相比，算出有變動的欄位。
 *
 * <p>專案裡原本有三套各自為政的比對邏輯，語意還不一樣。這裡收斂成一套，採最嚴格的
 * 規則：比較前先去頭尾空白，並把空字串視同 null。
 *
 * <p>為什麼要這樣：機構那套原本是「精確比對、null 當成空字串」，但
 * {@code updateLegalInfo} 會 trim、表單不會，於是重存一次沒改過的表單就會生出一筆
 * 假的「異動」。<strong>假異動是最快摧毀一份證據紀錄可信度的東西</strong>——看過幾次
 * 「明明沒改卻說我改了」之後，真的那筆也不會有人信。
 *
 * <p>注意「清空一個欄位」是真的變動，一定要記：從有電話改成沒電話正是爭議會問的事。
 * 只有「空白 ↔ null ↔ 全是空格」這種同義的寫法之間才算沒變。
 *
 * <p>記錄下來的 {@code before}/{@code after} 是原值（只把 null 寫成空字串），不是
 * 正規化後的值——正規化只用來判斷「有沒有變」，存的要是當時實際的內容。
 */
public final class FieldDiff {

    private FieldDiff() {
    }

    /**
     * 同鍵的兩份快照相比，回傳有變動的欄位，順序照 {@code after} 的插入順序。
     *
     * <p>兩份快照的鍵必須一致（都由同一個 snapshot 方法產生）；只出現在 {@code before}
     * 的鍵會被忽略。
     */
    public static List<FieldChange> between(Map<String, String> before, Map<String, String> after) {
        List<FieldChange> changes = new ArrayList<>();
        after.forEach((field, value) -> add(changes, field, before.get(field), value));
        return changes;
    }

    /** 有變動才加進去；沒變動不留痕跡。 */
    public static void add(List<FieldChange> changes, String field, String before, String after) {
        if (!Objects.equals(blankToNull(before), blankToNull(after))) {
            changes.add(new FieldChange(field, before == null ? "" : before, after == null ? "" : after));
        }
    }

    /** 保持插入順序的空快照，給各個 snapshot 方法當起點。 */
    public static Map<String, String> snapshot() {
        return new LinkedHashMap<>();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
