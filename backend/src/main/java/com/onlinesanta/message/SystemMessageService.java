package com.onlinesanta.message;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.claim.Claim;

/**
 * 系統訊息：狀態有變化時，在該願望的對話裡留一則通知（SPEC 15.3），並計入機構的未讀。
 *
 * <p>呼叫端在同一個交易裡呼叫——狀態變了、通知就一定在；狀態回滾了、通知也不會留下。
 * 刻意不走事件＋AFTER_COMMIT：通知是狀態的一部分記錄，不是事後的副作用。
 *
 * <p>系統通知只給機構看（認領者的對話不會出現它們）；文字取向是告訴機構「現在怎樣、
 * 下一步可以做什麼」。
 */
@Service
public class SystemMessageService {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy/M/d").withZone(ZoneId.of("Asia/Taipei"));

    private final MessageRepository messages;

    public SystemMessageService(MessageRepository messages) {
        this.messages = messages;
    }

    /** 認領者認領了願望：機構收到通知，認領者收到機構的招呼。 */
    @Transactional
    public void claimCreated(Claim claim) {
        UUID wishId = claim.getWish().getId();
        String deadline = claim.getShipDeadlineAt() == null
                ? "" : "，預計在 %s 前寄出".formatted(DATE.format(claim.getShipDeadlineAt()));
        post(wishId, claim.getId(), "%s 認領了這個願望%s。".formatted(claim.getDonor().getDisplayName(), deadline));
        messages.save(Message.welcome(wishId, claim.getId(),
                "謝謝你認領%s的願望！寄送或禮物有任何問題，都可以在這裡問我們。"
                        .formatted(claim.getWish().getChildAlias())));
    }

    /** 認領者取消認領。 */
    @Transactional
    public void claimCancelled(Claim claim) {
        post(claim.getWish().getId(), claim.getId(), "認領者取消了認領，這個願望已重新開放給其他人認領。");
    }

    /** 認領者回報寄送：附物流與追蹤碼，提醒機構收到後確認。 */
    @Transactional
    public void shipmentReported(Claim claim) {
        post(claim.getWish().getId(), claim.getId(),
                "認領者已回報寄送：%s，追蹤碼 %s。收到並檢查後，請按「確認收到並完成」。"
                        .formatted(claim.getTrackingCarrier(), claim.getTrackingNumber()));
    }

    /** 超過寄出期限仍未回報（機構設定「我自己決定」時）。 */
    @Transactional
    public void shipDeadlinePassed(Claim claim) {
        post(claim.getWish().getId(), claim.getId(),
                "已超過寄出期限（%s），認領者還沒回報寄送。可以先傳訊息詢問，或收回這個願望。"
                        .formatted(DATE.format(claim.getShipDeadlineAt())));
    }

    /** 逾期自動收回（機構設定「自動收回」時）。 */
    @Transactional
    public void autoReleased(Claim claim) {
        post(claim.getWish().getId(), claim.getId(),
                "認領者超過寄出期限仍未回報寄送，系統已自動收回，願望重新開放給其他人認領。");
    }

    /** 管理員核准願望。 */
    @Transactional
    public void wishApproved(UUID wishId) {
        post(wishId, null, "管理員已核准這個願望，現在可以在願望牆上被認領了。");
    }

    /** 管理員退回願望，附原因。 */
    @Transactional
    public void wishRejected(UUID wishId, String reason) {
        post(wishId, null, "管理員退回了這個願望，原因：%s。修改後請重新提交審核。".formatted(reason));
    }

    private void post(UUID wishId, UUID claimId, String body) {
        messages.save(Message.system(wishId, claimId, body));
    }
}
