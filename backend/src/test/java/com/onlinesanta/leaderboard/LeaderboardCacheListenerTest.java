package com.onlinesanta.leaderboard;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.onlinesanta.user.LeaderboardNameChangedEvent;

/**
 * 接線檢查：有人在榜上的名字變了（切換開關或改顯示名稱），名單快取就要被丟掉。
 *
 * <p>真正的事件派送發生在交易提交之後，而 API 整合測試整個跑在會回滾的交易裡，
 * 看不到 {@code AFTER_COMMIT}，所以這一段只能在這裡顧。
 */
@DisplayName("排行榜名單快取")
class LeaderboardCacheListenerTest {

    @Test
    @DisplayName("名字變了就丟掉名單快取")
    void evictsWhenNameChanges() {
        LeaderboardService leaderboard = mock(LeaderboardService.class);

        new LeaderboardCacheListener(leaderboard)
                .onNameChanged(new LeaderboardNameChangedEvent(UUID.randomUUID()));

        verify(leaderboard).evict();
    }
}
