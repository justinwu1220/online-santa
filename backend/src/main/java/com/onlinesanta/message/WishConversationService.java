package com.onlinesanta.message;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.auth.AppPrincipal;
import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.claim.Claim;
import com.onlinesanta.claim.ClaimRepository;
import com.onlinesanta.claim.ClaimStatus;
import com.onlinesanta.common.exception.BusinessRuleException;
import com.onlinesanta.common.exception.ResourceNotFoundException;
import com.onlinesanta.message.dto.MessageView;
import com.onlinesanta.message.dto.SendMessageRequest;
import com.onlinesanta.message.dto.WishConversationItem;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishRepository;

/**
 * 機構以「願望」為單位看對話：系統通知（認領、取消、寄出、逾期…）和目前認領者的訊息
 * 交錯在同一條時間線上。以前的認領（已收回或取消）的訊息不會混進來。
 *
 * <p>別家機構的願望一律 404，不透露它存在。
 */
@Service
public class WishConversationService {

    private final MessageRepository messages;
    private final MessageService messageService;
    private final WishRepository wishes;
    private final ClaimRepository claims;
    private final CurrentUserService currentUser;

    public WishConversationService(MessageRepository messages, MessageService messageService,
                                   WishRepository wishes, ClaimRepository claims,
                                   CurrentUserService currentUser) {
        this.messages = messages;
        this.messageService = messageService;
        this.wishes = wishes;
        this.claims = claims;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public WishConversationItem get(UUID wishId) {
        AppPrincipal viewer = currentUser.require();
        Wish wish = findOwned(wishId);
        Claim active = activeClaim(wish.getId());
        UUID activeId = active == null ? null : active.getId();

        List<MessageView> views = messages.findWishConversation(wishId, activeId).stream()
                .map(message -> MessageView.from(message, viewer.userId(), true))
                .toList();
        long unread = messages.countWishUnread(wishId, activeId, viewer.userId());
        return new WishConversationItem(
                active == null ? null : active.getDonor().getDisplayName(),
                active != null && !active.getStatus().isTerminal(),
                unread, views);
    }

    @Transactional
    public MessageView send(UUID wishId, SendMessageRequest request) {
        Wish wish = findOwned(wishId);
        Claim active = activeClaim(wish.getId());
        if (active == null) {
            throw new BusinessRuleException("NO_ACTIVE_CLAIM", "這個願望目前沒有認領者，沒有對象可以傳訊息");
        }
        return messageService.send(active.getId(), request);
    }

    /** 切到對話分頁時呼叫：把系統通知與認領者傳來的訊息都標為已讀。 */
    @Transactional
    public long markRead(UUID wishId) {
        AppPrincipal reader = currentUser.require();
        Wish wish = findOwned(wishId);
        Claim active = activeClaim(wish.getId());

        List<Message> system = messages.findByWishIdAndKindAndReadAtIsNull(wishId, MessageKind.SYSTEM);
        system.forEach(Message::markRead);
        long count = system.size();
        if (active != null) {
            count += messageService.markRead(active.getId());
        }
        return count;
    }

    /** 側邊選單紅色數字：所有願望的未讀總數。 */
    @Transactional(readOnly = true)
    public long unreadTotal() {
        AppPrincipal reader = currentUser.require();
        return messages.countOrganizationUnread(
                currentUser.requireOrganizationId(), reader.userId(), ClaimStatus.occupyingStates());
    }

    private Claim activeClaim(UUID wishId) {
        return claims.findFirstByWishIdAndStatusIn(wishId, ClaimStatus.occupyingStates()).orElse(null);
    }

    private Wish findOwned(UUID wishId) {
        UUID organizationId = currentUser.requireOrganizationId();
        Wish wish = wishes.findWithOrganizationById(wishId)
                .orElseThrow(() -> ResourceNotFoundException.of("願望", wishId));
        if (!wish.getOrganization().getId().equals(organizationId)) {
            throw ResourceNotFoundException.of("願望", wishId);
        }
        return wish;
    }
}
