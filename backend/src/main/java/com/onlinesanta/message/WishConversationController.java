package com.onlinesanta.message;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.message.dto.MessageView;
import com.onlinesanta.message.dto.SendMessageRequest;
import com.onlinesanta.message.dto.WishConversationItem;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/organizations/me")
@Tag(name = "機構對話", description = "機構以願望為單位看系統通知與認領者的對話")
public class WishConversationController {

    private final WishConversationService conversations;
    private final TeamChatService teamChat;

    public WishConversationController(WishConversationService conversations, TeamChatService teamChat) {
        this.conversations = conversations;
        this.teamChat = teamChat;
    }

    @GetMapping("/wishes/{wishId}/conversation")
    @Operation(summary = "願望的對話",
            description = "系統通知與目前認領者的訊息依時間排列；還沒有人認領時只有系統通知")
    public WishConversationItem conversation(@PathVariable UUID wishId) {
        return conversations.get(wishId);
    }

    @PostMapping("/wishes/{wishId}/conversation")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "傳訊息給目前的認領者", description = "沒有認領者時回 409（NO_ACTIVE_CLAIM）")
    public MessageView send(@PathVariable UUID wishId, @Valid @RequestBody SendMessageRequest request) {
        return conversations.send(wishId, request);
    }

    @PostMapping("/wishes/{wishId}/conversation/read")
    @Operation(summary = "標記這個願望的對話為已讀")
    public Map<String, Long> read(@PathVariable UUID wishId) {
        return Map.of("marked", conversations.markRead(wishId));
    }

    @GetMapping("/unread")
    @Operation(summary = "未讀總數", description = "wishes＝所有願望的對話，team＝與團隊的對話；側邊選單的紅色數字")
    public Map<String, Long> unread() {
        return Map.of("wishes", conversations.unreadTotal(), "team", teamChat.unreadCount());
    }
}
