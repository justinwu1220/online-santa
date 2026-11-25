package com.onlinesanta.leaderboard;

import java.time.Duration;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.auth.AppPrincipal;
import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.leaderboard.dto.LeaderboardView;
import com.onlinesanta.leaderboard.dto.MyAchievementView;
import com.onlinesanta.leaderboard.dto.PodiumHistoryView;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 聖誕老人排行榜。
 *
 * <p>名單是公開的（願望牆的訪客也看得到），所以回應裡沒有任何識別資料；
 * 「我的成就」需要登入，只回自己的數字。
 *
 * <p><strong>流量與成本</strong>：首頁入口每開一次願望牆就會打一次名單，這是整個功能唯一的
 * 高流量入口，而這個後端是 scale to zero 的 Cloud Run——醒著的每一秒都要錢。所以：
 * <ul>
 *   <li>名單對所有人回同一份（沒有「這一列是我」），才能一路快取到 Firebase Hosting 的 CDN，
 *       重複請求連執行個體都不必醒來。對應的 CDN 設定在 {@code firebase.json}；</li>
 *   <li>服務層另有 {@value LeaderboardService#CACHE_SECONDS} 秒的記憶體快取，擋下同一個執行個體內的重算；</li>
 *   <li>首頁入口帶 {@code limit=3}，不必為了三顆膠囊搬整份名單；</li>
 *   <li>「我的成就」人人不同，{@code private, no-store} 且即時計算——它只有登入者開排行榜頁時才會打。</li>
 * </ul>
 */
@RestController
@Tag(name = "排行榜", description = "依機構確認收到的禮物份數排名")
public class LeaderboardController {

    private final LeaderboardService leaderboard;
    private final CurrentUserService currentUser;
    private final UserRepository users;

    public LeaderboardController(LeaderboardService leaderboard, CurrentUserService currentUser,
                                 UserRepository users) {
        this.leaderboard = leaderboard;
        this.currentUser = currentUser;
        this.users = users;
    }

    @GetMapping("/api/leaderboard")
    @Operation(summary = "排行榜名單（公開）",
            description = "名次、顯示名稱或「匿名的聖誕老人」、份數與稱號。不含使用者 id、信箱、禮物、願望或機構資訊，"
                    + "也不標「這一列是我」——回應對所有人相同才快取得住。"
                    + "limit 只取前幾名（首頁入口用 limit=3）")
    public ResponseEntity<LeaderboardView> board(@RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(LeaderboardService.CACHE_SECONDS)).cachePublic())
                .body(leaderboard.board(limit));
    }

    @GetMapping("/api/leaderboard/podiums")
    @Operation(summary = "歷屆前三名（公開）",
            description = "每一屆活動的前三名，新的一屆在前；並列時同名次，所以一屆可能超過三筆")
    public ResponseEntity<PodiumHistoryView> podiums() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(leaderboard.podiumHistory());
    }

    @GetMapping("/api/me/leaderboard")
    @Operation(summary = "我的成就",
            description = "當期份數、名次，以及累計解鎖的稱號與下一個稱號。即時計算，不走快取")
    public ResponseEntity<MyAchievementView> mine() {
        AppPrincipal principal = currentUser.require();
        boolean showName = users.findById(principal.userId()).map(User::isLeaderboardOptIn).orElse(false);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(leaderboard.mine(principal.userId(), showName));
    }
}
