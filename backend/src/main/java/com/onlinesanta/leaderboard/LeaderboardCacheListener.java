package com.onlinesanta.leaderboard;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.onlinesanta.user.LeaderboardNameChangedEvent;

/**
 * 有人在榜上顯示的名字變了（切換開關或改顯示名稱）之後，把名單快取丟掉。
 *
 * <p>重算只是兩句 group by，而改名字的次數遠少於看排行榜的次數，
 * 直接整份丟掉最簡單。交易提交後才處理，重算才讀得到新值。
 *
 * <p>{@code fallbackExecution}：沒有交易時（例如測試直接發事件）也照樣處理。
 */
@Component
public class LeaderboardCacheListener {

    private final LeaderboardService leaderboard;

    public LeaderboardCacheListener(LeaderboardService leaderboard) {
        this.leaderboard = leaderboard;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNameChanged(LeaderboardNameChangedEvent event) {
        leaderboard.evict();
    }
}
