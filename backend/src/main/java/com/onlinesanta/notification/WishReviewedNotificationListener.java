package com.onlinesanta.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.onlinesanta.event.WishReviewedEvent;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishRepository;

/**
 * 願望審核結果（核准／退回）→ 通知機構的 contactEmail。
 *
 * <p>站內訊息本來就有：{@code SystemMessageService.wishApproved/wishRejected} 會把結果
 * 貼在該筆願望上。但那要機構主動登入才看得到，而活動只有三到四週——被退回的願望如果
 * 沒人注意，就會一直卡在草稿狀態，等於少了一個孩子的願望上架。信件的作用是把人帶回來。
 *
 * <p>只寄給機構。願望還沒被認領，沒有認領者可通知；已認領的願望也不會走審核流程
 * （內容在認領當下就鎖定了）。
 */
@Component
public class WishReviewedNotificationListener {

    private final WishRepository wishes;
    private final NotificationService notifications;
    private final NotificationProperties properties;

    public WishReviewedNotificationListener(WishRepository wishes,
                                            NotificationService notifications,
                                            NotificationProperties properties) {
        this.wishes = wishes;
        this.notifications = notifications;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWishReviewed(WishReviewedEvent event) {
        // 用 findWithOrganizationById 而不是 findById：organization 是 LAZY，而 AFTER_COMMIT
        // 的當下交易已經結束，這裡再碰它就會 LazyInitializationException
        wishes.findWithOrganizationById(event.wishId())
                .ifPresent(wish -> notify(wish, event.approved(), event.reason()));
    }

    private void notify(Wish wish, boolean approved, String reason) {
        Organization organization = wish.getOrganization();
        String title = wish.getTitle();
        String subject = approved
                ? "願望「%s」已通過審核".formatted(title)
                : "願望「%s」需要修改".formatted(title);
        String body = approved
                ? """
                %s 您好，

                您上架的願望「%s」已通過審核，現在就在願望牆上等待民眾認領。
                被認領時我們會再寄信通知您。

                查看與管理您的願望：
                %s/org

                線上聖誕老人
                """.formatted(organization.getName(), title, properties.publicUrl())
                : """
                %s 您好，

                您上架的願望「%s」這次沒有通過審核。
                原因：%s

                請到機構後台修改內容後重新送出審核。

                %s/org

                線上聖誕老人
                """.formatted(organization.getName(), title,
                        reason == null || reason.isBlank() ? "（未填寫）" : reason,
                        properties.publicUrl());
        notifications.send(organization.getContactEmail(), subject, body);
    }
}
