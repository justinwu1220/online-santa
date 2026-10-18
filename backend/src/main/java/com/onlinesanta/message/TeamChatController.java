package com.onlinesanta.message;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.message.dto.SendMessageRequest;
import com.onlinesanta.message.dto.TeamMessageView;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/organizations/me/team-chat")
@Tag(name = "團隊對話", description = "機構與線上聖誕老人團隊的對話，與認領無關")
public class TeamChatController {

    private final TeamChatService teamChat;

    public TeamChatController(TeamChatService teamChat) {
        this.teamChat = teamChat;
    }

    @GetMapping
    @Operation(summary = "與團隊的對話")
    public List<TeamMessageView> list() {
        return teamChat.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "傳訊息給團隊")
    public TeamMessageView send(@Valid @RequestBody SendMessageRequest request) {
        return teamChat.send(request);
    }

    @PostMapping("/read")
    @Operation(summary = "標記團隊訊息為已讀")
    public Map<String, Long> read() {
        return Map.of("marked", teamChat.markRead());
    }
}
