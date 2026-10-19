package com.onlinesanta.message;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.auth.AppPrincipal;
import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.common.exception.ResourceNotFoundException;
import com.onlinesanta.message.dto.AdminTeamThreadView;
import com.onlinesanta.message.dto.SendMessageRequest;
import com.onlinesanta.message.dto.TeamMessageView;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;

/**
 * 平台團隊端的「與機構的對話」：每個機構一條對話串（與認領無關）。
 * 機構送出的訊息由團隊讀；團隊回覆的由機構讀（機構端已讀見 {@link TeamChatService}）。
 */
@Service
public class AdminTeamChatService {

    private final TeamMessageRepository messages;
    private final OrganizationRepository organizations;
    private final CurrentUserService currentUser;

    public AdminTeamChatService(TeamMessageRepository messages, OrganizationRepository organizations,
                                CurrentUserService currentUser) {
        this.messages = messages;
        this.organizations = organizations;
        this.currentUser = currentUser;
    }

    /** 有對話的機構：還有未讀的排前面，其次依最新訊息時間新到舊。 */
    @Transactional(readOnly = true)
    public List<AdminTeamThreadView> threads() {
        currentUser.requireAdmin();
        Map<UUID, Long> unread = new HashMap<>();
        for (Object[] row : messages.countUnreadByTeamPerOrganization()) {
            unread.put((UUID) row[0], (Long) row[1]);
        }
        List<TeamMessage> latest = messages.findLatestPerOrganization();
        Map<UUID, Organization> orgs = organizations
                .findAllById(latest.stream().map(TeamMessage::getOrganizationId).toList())
                .stream().collect(Collectors.toMap(Organization::getId, o -> o));
        return latest.stream()
                .filter(m -> orgs.containsKey(m.getOrganizationId()))
                .map(m -> new AdminTeamThreadView(m.getOrganizationId(),
                        orgs.get(m.getOrganizationId()).getName(), m.getBody(), m.isFromTeam(),
                        m.getCreatedAt(), unread.getOrDefault(m.getOrganizationId(), 0L)))
                .sorted(Comparator.comparing((AdminTeamThreadView t) -> t.unread() > 0 ? 0 : 1)
                        .thenComparing(AdminTeamThreadView::lastAt, Comparator.reverseOrder()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TeamMessageView> messagesOf(UUID organizationId) {
        currentUser.requireAdmin();
        requireOrganization(organizationId);
        return messages.findByOrganizationIdOrderByCreatedAtAscIdAsc(organizationId)
                .stream().map(TeamMessageView::from).toList();
    }

    @Transactional
    public TeamMessageView reply(UUID organizationId, SendMessageRequest request) {
        AppPrincipal admin = currentUser.requireAdmin();
        requireOrganization(organizationId);
        return TeamMessageView.from(messages.save(
                TeamMessage.fromTeam(organizationId, admin.userId(), request.body().strip())));
    }

    @Transactional
    public long markRead(UUID organizationId) {
        currentUser.requireAdmin();
        requireOrganization(organizationId);
        List<TeamMessage> unread = messages.findByOrganizationIdAndFromTeamFalseAndReadAtIsNull(organizationId);
        unread.forEach(TeamMessage::markRead);
        return unread.size();
    }

    /** 選單上的紅色數字：所有機構還沒讀的訊息。 */
    @Transactional(readOnly = true)
    public long unreadTotal() {
        currentUser.requireAdmin();
        return messages.countByFromTeamFalseAndReadAtIsNull();
    }

    private void requireOrganization(UUID organizationId) {
        if (!organizations.existsById(organizationId)) {
            throw ResourceNotFoundException.of("機構", organizationId);
        }
    }
}
