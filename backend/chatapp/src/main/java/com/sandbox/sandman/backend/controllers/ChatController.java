// backend/src/main/java/com/sandbox/sandman/backend/controllers/ChatController.java
package com.sandbox.sandman.backend.controllers;

import com.sandbox.sandman.backend.model.dto.ChatDto.ChatRequestDto;
import com.sandbox.sandman.backend.model.dto.ChatDto.ChatResponseDto;
import com.sandbox.sandman.backend.model.dto.ChatDto.MessageDto;
import com.sandbox.sandman.backend.services.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("${app.api.prefix.chat-app}")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @GetMapping("/message/history/{roomId}")
    public ResponseEntity<List<MessageDto>> getChatHistory(@PathVariable Long roomId) {
        List<MessageDto> history = chatService.getChatHistoryByRoom(roomId);
        return ResponseEntity.ok(history);
    }

    @PostMapping("/message")
    public ResponseEntity<ChatResponseDto> chatWithAi(@RequestBody ChatRequestDto request) {
        String reply = chatService.getAiResponse(request);
        return ResponseEntity.ok(new ChatResponseDto(reply));
    }

}
