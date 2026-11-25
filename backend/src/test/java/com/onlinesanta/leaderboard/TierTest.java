package com.onlinesanta.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TierTest {

    @Test
    @DisplayName("稱號看累計份數：1／3／5／10 份各解鎖一級，門檻之間維持前一級")
    void tiersUnlockAtThresholds() {
        assertThat(Tier.of(0)).isNull();
        assertThat(Tier.of(1)).isEqualTo(Tier.ELF);
        assertThat(Tier.of(2)).isEqualTo(Tier.ELF);
        assertThat(Tier.of(3)).isEqualTo(Tier.REINDEER);
        assertThat(Tier.of(4)).isEqualTo(Tier.REINDEER);
        assertThat(Tier.of(5)).isEqualTo(Tier.CAPTAIN);
        assertThat(Tier.of(9)).isEqualTo(Tier.CAPTAIN);
        assertThat(Tier.of(10)).isEqualTo(Tier.LEGEND);
        assertThat(Tier.of(99)).isEqualTo(Tier.LEGEND);
    }

    @Test
    @DisplayName("下一個稱號是還沒達到的最低一級；已是最高一級時沒有下一個")
    void nextTierIsTheLowestNotYetReached() {
        assertThat(Tier.next(0)).isEqualTo(Tier.ELF);
        assertThat(Tier.next(1)).isEqualTo(Tier.REINDEER);
        assertThat(Tier.next(4)).isEqualTo(Tier.CAPTAIN);
        assertThat(Tier.next(5)).isEqualTo(Tier.LEGEND);
        assertThat(Tier.next(10)).isNull();
    }
}
