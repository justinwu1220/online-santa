package com.onlinesanta.message;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.message.dto.AdminTeamThreadView;
import com.onlinesanta.message.dto.SendMessageRequest;
import com.onlinesanta.message.dto.TeamMessageView;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/team-chat")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理後台", description = "平台團隊與機構的對話")
public class AdminTeamChatController {

    private final AdminTeamChatService teamChat;

    public AdminTeamChatController(AdminTeamChatService teamChat) {
        this.teamChat = teamChat;
    }

    @GetMapping
    @Operation(summary = "有對話的機構清單", description = "未讀的排前面，其次依最新訊息時間")
    public List<AdminTeamThreadView> threads() {
        return teamChat.threads();
    }

    @GetMapping("/unread")
    @Operation(summary = "團隊還沒讀的機構訊息總數")
    public Map<String, Long> unread() {
        return Map.of("unread", teamChat.unreadTotal());
    }

    @GetMapping("/{organizationId}")
    @Operation(summary = "與某機構的對話")
    public List<TeamMessageView> messages(@PathVariable UUID organizationId) {
        return teamChat.messagesOf(organizationId);
    }

    @PostMapping("/{organizationId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "回覆機構")
    public TeamMessageView reply(@PathVariable UUID organizationId, @Valid @RequestBody SendMessageRequest request) {
        return teamChat.reply(organizationId, request);
    }

    @PostMapping("/{organizationId}/read")
    @Operation(summary = "標記機構的訊息為已讀")
    public Map<String, Long> read(@PathVariable UUID organizationId) {
        return Map.of("marked", teamChat.markRead(organizationId));
    }
}
