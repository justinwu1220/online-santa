package com.onlinesanta.message;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.auth.AppPrincipal;
import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimService;
import com.onlinesanta.common.exception.BusinessRuleException;
import com.onlinesanta.event.NewMessageEvent;
import com.onlinesanta.message.dto.MessageView;
import com.onlinesanta.message.dto.SendMessageRequest;

/**
 * 認領內的雙向對話。
 *
 * <p>可見性直接沿用 {@link ClaimService#getVisibleById}——訊息的權限範圍與認領本身
 * 完全相同（捐贈者與願望所屬機構），不需要另一套判斷。
 */
@Service
public class MessageService {

    private final MessageRepository messages;
    private final ClaimService claims;
    private final CurrentUserService currentUser;
    private final ApplicationEventPublisher eventPublisher;

    public MessageService(MessageRepository messages,
                          ClaimService claims,
                          CurrentUserService currentUser,
                          ApplicationEventPublisher eventPublisher) {
        this.messages = messages;
        this.claims = claims;
        this.currentUser = currentUser;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<MessageView> list(UUID claimId) {
        AppPrincipal viewer = currentUser.require();
        claims.getVisibleById(claimId);

        return messages.findByClaimIdOrderByCreatedAtAsc(claimId).stream()
                .map(message -> MessageView.from(message, viewer.userId()))
                .toList();
    }

    @Transactional
    public MessageView send(UUID claimId, SendMessageRequest request) {
        AppPrincipal sender = currentUser.require();
        Claim claim = claims.getVisibleById(claimId);

        // 認領結束後就不該再有新對話——該說的話在流程中都說完了
        if (claim.getStatus().isTerminal()) {
            throw new BusinessRuleException("CLAIM_CLOSED",
                    "這筆認領已經結束（%s），無法再傳送訊息".formatted(claim.getStatus()));
        }

        // 防轟炸：這個判斷必須在存這則新訊息「之前」算，不然這則訊息自己一定會讓
        // 對方的未讀數變成非 0，條件永遠不成立。如果對方已經有這個寄件人先前寄的
        // 訊息還沒讀，就不再寄信——等對方讀了，下一則新訊息才會再次觸發通知。
        boolean recipientHasUnreadFromSender = messages
                .countByClaimIdAndReadAtIsNullAndSenderUserId(claimId, sender.userId()) > 0;

        Message message = messages.save(
                Message.from(claimId, sender.userId(), request.body().strip()));

        if (!recipientHasUnreadFromSender) {
            eventPublisher.publishEvent(new NewMessageEvent(claimId, sender.userId()));
        }

        return MessageView.from(message, sender.userId());
    }

    /**
     * 把對方傳來的未讀訊息標記為已讀。
     *
     * <p>刻意做成獨立端點而非在讀取清單時順手標記：GET 不應該有副作用，
     * 而且前端可能只是預覽而非真的打開對話。
     */
    @Transactional
    public int markRead(UUID claimId) {
        AppPrincipal reader = currentUser.require();
        claims.getVisibleById(claimId);

        List<Message> unread = messages
                .findByClaimIdAndReadAtIsNullAndSenderUserIdNot(claimId, reader.userId());
        unread.forEach(Message::markRead);
        return unread.size();
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID claimId) {
        AppPrincipal reader = currentUser.require();
        return messages.countByClaimIdAndReadAtIsNullAndSenderUserIdNot(claimId, reader.userId());
    }

    /** 清單頁用：一次算出整頁認領的未讀數。 */
    @Transactional(readOnly = true)
    public Map<UUID, Long> unreadCounts(Collection<UUID> claimIds, UUID readerId) {
        if (claimIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : messages.countUnreadByClaimIds(claimIds, readerId)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }
}
