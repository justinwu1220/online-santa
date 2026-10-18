package com.onlinesanta.message;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.auth.AppPrincipal;
import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.message.dto.SendMessageRequest;
import com.onlinesanta.message.dto.TeamMessageView;

/** 機構與平台團隊的對話。平台端的回覆介面隨管理員後台（階段三）一起做。 */
@Service
public class TeamChatService {

    static final String GREETING =
            "您好！貴機構的資料已經收到。審核期間可以先建立願望草稿，有任何問題都可以在這裡問我們。";

    private final TeamMessageRepository messages;
    private final CurrentUserService currentUser;

    public TeamChatService(TeamMessageRepository messages, CurrentUserService currentUser) {
        this.messages = messages;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<TeamMessageView> list() {
        return messages.findByOrganizationIdOrderByCreatedAtAscIdAsc(currentUser.requireOrganizationId())
                .stream().map(TeamMessageView::from).toList();
    }

    @Transactional
    public TeamMessageView send(SendMessageRequest request) {
        AppPrincipal sender = currentUser.require();
        UUID organizationId = currentUser.requireOrganizationId();
        return TeamMessageView.from(messages.save(
                TeamMessage.fromOrganization(organizationId, sender.userId(), request.body().strip())));
    }

    @Transactional
    public long markRead() {
        List<TeamMessage> unread = messages
                .findByOrganizationIdAndFromTeamTrueAndReadAtIsNull(currentUser.requireOrganizationId());
        unread.forEach(TeamMessage::markRead);
        return unread.size();
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return messages.countByOrganizationIdAndFromTeamTrueAndReadAtIsNull(
                currentUser.requireOrganizationId());
    }

    /** 機構送出申請時，團隊先打聲招呼（在機構註冊的交易裡呼叫）。 */
    @Transactional
    public void greetNewOrganization(UUID organizationId) {
        messages.save(TeamMessage.fromTeam(organizationId, null, GREETING));
    }
}
