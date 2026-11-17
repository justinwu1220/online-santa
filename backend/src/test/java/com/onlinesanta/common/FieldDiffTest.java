package com.onlinesanta.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.onlinesanta.common.dto.FieldChange;

@DisplayName("欄位比對（個資異動紀錄的判斷依據）")
class FieldDiffTest {

    @Test
    @DisplayName("空白、null 與全是空格視為同一個值：重存沒改過的表單不會生出假異動")
    void blankIsNull() {
        List<FieldChange> changes = new ArrayList<>();
        FieldDiff.add(changes, "phone", null, "");
        FieldDiff.add(changes, "address", "", "   ");
        FieldDiff.add(changes, "website", "   ", null);
        assertThat(changes).isEmpty();
    }

    @Test
    @DisplayName("只差在前後空白不算異動——updateLegalInfo 會 trim、表單不會，這裡要吸收掉")
    void trimming() {
        List<FieldChange> changes = new ArrayList<>();
        FieldDiff.add(changes, "withholdingTaxId", "12345678", "  12345678  ");
        assertThat(changes).isEmpty();
    }

    @Test
    @DisplayName("清空一個欄位是真的異動，一定要記")
    void clearingIsAChange() {
        List<FieldChange> changes = new ArrayList<>();
        FieldDiff.add(changes, "phone", "0912-345-678", null);

        assertThat(changes).hasSize(1);
        assertThat(changes.getFirst().field()).isEqualTo("phone");
        assertThat(changes.getFirst().before()).isEqualTo("0912-345-678");
        // null 寫成空字串，不是漏掉
        assertThat(changes.getFirst().after()).isEmpty();
    }

    @Test
    @DisplayName("記下來的是原值，不是正規化後的值——正規化只用來判斷有沒有變")
    void keepsRawValues() {
        List<FieldChange> changes = new ArrayList<>();
        FieldDiff.add(changes, "name", " 王小明 ", "王大明");

        assertThat(changes).hasSize(1);
        assertThat(changes.getFirst().before()).isEqualTo(" 王小明 ");
    }

    @Test
    @DisplayName("兩份快照相比只回傳有變的欄位，順序照 after 的插入順序")
    void betweenSnapshots() {
        Map<String, String> before = FieldDiff.snapshot();
        before.put("name", "舊名");
        before.put("phone", "0912-000-000");
        before.put("address", "台北市");

        Map<String, String> after = FieldDiff.snapshot();
        after.put("name", "新名");
        after.put("phone", "0912-000-000");
        after.put("address", "高雄市");

        assertThat(FieldDiff.between(before, after))
                .extracting(FieldChange::field)
                .containsExactly("name", "address");
    }

    @Test
    @DisplayName("完全沒改就是空清單——recorder 靠這個決定不寫任何東西")
    void noChange() {
        Map<String, String> snapshot = FieldDiff.snapshot();
        snapshot.put("name", "一樣");
        assertThat(FieldDiff.between(snapshot, FieldDiff.snapshot())).isEmpty();
        assertThat(FieldDiff.between(snapshot, snapshot)).isEmpty();
    }
}
