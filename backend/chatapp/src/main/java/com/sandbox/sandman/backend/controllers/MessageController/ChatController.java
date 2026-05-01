// backend/src/main/java/com/sandbox/sandman/backend/controllers/ChatController.java
package com.sandbox.sandman.backend.controllers.MessageController;

import com.sandbox.sandman.backend.commonauth.CurrentUser;
import com.sandbox.sandman.backend.model.dto.MessageDto.ChatHistoryResponse;
import com.sandbox.sandman.backend.model.dto.MessageDto.ChatRequestDto;
import com.sandbox.sandman.backend.model.dto.MessageDto.ChatResponseDto;
import com.sandbox.sandman.backend.services.MessageService.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${app.api.prefix.chat-app}")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final CurrentUser currentUser;

    @GetMapping("/chat/history/{roomId}")
    public ResponseEntity<ChatHistoryResponse> getChatHistory(
            @PathVariable Long roomId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "20") int limit) {
        ChatHistoryResponse history = chatService.getChatHistoryByRoom(roomId, beforeId, limit);
        return ResponseEntity.ok(history);
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponseDto> chatWithAi(@RequestBody ChatRequestDto request) {
        String reply = chatService.getAiResponse(currentUser.requireUserId(), request);
        return ResponseEntity.ok(new ChatResponseDto(reply));
    }

}
