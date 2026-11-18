package com.onlinesanta.claim;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.onlinesanta.notification.MailFields;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.ReleasePolicy;
import com.onlinesanta.user.User;
import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishCategory;

/** 寄送期限的暫停與恢復：順延的金額、提醒標記、恢復信上的期限欄位。 */
class ClaimDeadlinePauseTest {

    private static final Instant T0 = Instant.parse("2026-12-01T00:00:00Z");

    private static Claim claimWithDeadline(Instant deadline) {
        Organization org = Organization.register("機構", "承辦", "org@example.org", null, null, null);
        Wish wish = Wish.draft(org, "小星", AgeRange.AGE_7_9, "畫畫", "願望", "描述",
                WishCategory.ART, PriceRange.UNDER_500);
        User donor = User.newDonor("donor@example.org", "熱心民眾");
        return Claim.open(wish, donor, ReleasePolicy.MANUAL, null, deadline, null);
    }

    @Test
    @DisplayName("恢復時把暫停的時間原封不動加回寄送期限")
    void resumeAddsTheExactPausedDuration() {
        Instant deadline = T0.plus(Duration.ofDays(7));
        Claim claim = claimWithDeadline(deadline);

        claim.pauseDeadline(T0.plus(Duration.ofDays(1)));
        claim.resumeDeadline(T0.plus(Duration.ofDays(4)));      // 暫停 3 天

        assertThat(claim.getShipDeadlineAt()).isEqualTo(deadline.plus(Duration.ofDays(3)));
        assertThat(claim.getDeadlinePausedAt()).isNull();
    }

    @Test
    @DisplayName("恢復時清除「已寄過到期提醒」的標記，新的期限臨近時會再提醒一次")
    void resumeClearsTheReminderMarker() {
        Claim claim = claimWithDeadline(T0.plus(Duration.ofDays(2)));
        claim.markDeadlineReminderSent();
        assertThat(claim.getDeadlineReminderSentAt()).isNotNull();

        claim.pauseDeadline(T0);
        claim.resumeDeadline(T0.plus(Duration.ofDays(5)));

        assertThat(claim.getDeadlineReminderSentAt()).isNull();
    }

    @Test
    @DisplayName("恢復時也清除「已寄過逾期信」的標記，順延後的期限再過時能再通知一次")
    void resumeClearsTheOverdueNoticeMarker() {
        Claim claim = claimWithDeadline(T0.minus(Duration.ofDays(1)));
        claim.markOverdueNoticeSent();

        claim.pauseDeadline(T0);
        claim.resumeDeadline(T0.plus(Duration.ofDays(2)));

        assertThat(claim.getOverdueNoticeSentAt()).isNull();
    }

    @Test
    @DisplayName("沒有暫停過的認領呼叫 resume 不會動到期限與提醒標記")
    void resumeWithoutPauseChangesNothing() {
        Instant deadline = T0.plus(Duration.ofDays(2));
        Claim claim = claimWithDeadline(deadline);
        claim.markDeadlineReminderSent();

        claim.resumeDeadline(T0.plus(Duration.ofDays(1)));

        assertThat(claim.getShipDeadlineAt()).isEqualTo(deadline);
        assertThat(claim.getDeadlineReminderSentAt()).isNotNull();
    }

    @Test
    @DisplayName("恢復信：順延後的期限還在未來就顯示日期")
    void resumedDeadlineInTheFutureIsShown() {
        Claim claim = claimWithDeadline(T0.plus(Duration.ofDays(7)));
        var fields = MailFields.resumedDeadline(claim, T0);

        assertThat(fields.get("shipDeadline")).isNotNull();
        assertThat(fields.get("deadlinePassed")).isEqualTo(false);
    }

    @Test
    @DisplayName("恢復信：順延後期限仍在過去，就不寫日期，改請認領者到認領頁確認")
    void resumedDeadlineAlreadyPassedIsNotShown() {
        // 期限 12/02，12/03 凍結、12/04 恢復：暫停 1 天後期限變 12/03，仍早於「現在」12/10
        Claim claim = claimWithDeadline(T0.plus(Duration.ofDays(1)));
        claim.pauseDeadline(T0.plus(Duration.ofDays(2)));
        claim.resumeDeadline(T0.plus(Duration.ofDays(3)));

        var fields = MailFields.resumedDeadline(claim, T0.plus(Duration.ofDays(9)));

        assertThat(fields.get("shipDeadline")).isNull();
        assertThat(fields.get("deadlinePassed")).isEqualTo(true);
    }
}
