package com.sandbox.sandman.backend.controllers;

import com.sandbox.sandman.backend.model.dto.ConversationDto;
import com.sandbox.sandman.backend.model.dto.MessageDto;
import com.sandbox.sandman.backend.model.dto.MessageSendRequest;
import com.sandbox.sandman.backend.model.dto.PageResponse;
import com.sandbox.sandman.backend.model.entity.Conversation;
import com.sandbox.sandman.backend.security.CurrentUser;
import com.sandbox.sandman.backend.services.BlobStorageService;
import com.sandbox.sandman.backend.services.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("${app.api.prefix.b-post}")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final BlobStorageService blobStorageService;
    private final CurrentUser currentUser;

    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationDto>> list() {
        return ResponseEntity.ok(messageService.listForUser(currentUser.requireUserId()));
    }

    @PostMapping("/conversations")
    public ResponseEntity<Map<String, Object>> openWith(@RequestBody Map<String, Long> body) {
        Long otherId = body.get("userId");
        Conversation c = messageService.getOrCreateConversation(currentUser.requireUserId(), otherId);
        return ResponseEntity.ok(Map.of("id", c.getId()));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<PageResponse<MessageDto>> history(
            @PathVariable Long conversationId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "30") int limit) {
        return ResponseEntity.ok(messageService.history(currentUser.requireUserId(), conversationId, beforeId, limit));
    }

    @PostMapping("/conversations/{conversationId}/read")
    public ResponseEntity<Map<String, Integer>> markRead(@PathVariable Long conversationId) {
        int updated = messageService.markRead(currentUser.requireUserId(), conversationId);
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    @PostMapping("/messages")
    public ResponseEntity<MessageDto> send(@RequestBody MessageSendRequest req) {
        return ResponseEntity.ok(messageService.send(currentUser.requireUserId(), req));
    }

    @PostMapping("/messages/upload-image")
    public ResponseEntity<Map<String, String>> uploadImage(@RequestParam("imageFile") MultipartFile file) {
        var result = blobStorageService.uploadAndReturnUrl(file, "b-post/messages");
        return ResponseEntity.ok(Map.of("url", result.publicUrl(), "path", result.objectPath()));
    }

    @PostMapping("/posts/upload-image")
    public ResponseEntity<Map<String, String>> uploadPostImage(@RequestParam("imageFile") MultipartFile file) {
        var result = blobStorageService.uploadAndReturnUrl(file, "b-post/posts");
        return ResponseEntity.ok(Map.of("url", result.publicUrl(), "path", result.objectPath()));
    }
}
