package com.onlinesanta.message;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.message.dto.MessageView;
import com.onlinesanta.message.dto.SendMessageRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** 認領內的對話。捐贈者與願望所屬機構雙方可用，其他人一律 404。 */
@RestController
@RequestMapping("/api/claims/{claimId}/messages")
@Tag(name = "訊息", description = "捐贈者與機構的對話")
public class MessageController {

    private final MessageService messages;

    public MessageController(MessageService messages) {
        this.messages = messages;
    }

    @GetMapping
    @Operation(summary = "取得對話內容")
    public List<MessageView> list(@PathVariable UUID claimId) {
        return messages.list(claimId);
    }

    @PostMapping
    @Operation(summary = "傳送訊息", description = "認領結束後無法再傳送")
    public MessageView send(@PathVariable UUID claimId,
                            @Valid @RequestBody SendMessageRequest request) {
        return messages.send(claimId, request);
    }

    @PostMapping("/mark-read")
    @Operation(summary = "標記對方的訊息為已讀")
    public Map<String, Integer> markRead(@PathVariable UUID claimId) {
        return Map.of("markedRead", messages.markRead(claimId));
    }
}
