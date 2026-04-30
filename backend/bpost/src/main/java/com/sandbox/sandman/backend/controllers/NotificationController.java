package com.sandbox.sandman.backend.controllers;

import com.sandbox.sandman.backend.model.dto.NotificationDto;
import com.sandbox.sandman.backend.security.CurrentUser;
import com.sandbox.sandman.backend.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("${app.api.prefix.b-post}/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUser currentUser;

    @GetMapping
    public ResponseEntity<List<NotificationDto>> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "30") int limit) {
        return ResponseEntity.ok(notificationService.list(currentUser.requireUserId(), unreadOnly, limit));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount() {
        return ResponseEntity.ok(Map.of("count", notificationService.unreadCount(currentUser.requireUserId())));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> read(@PathVariable Long id) {
        notificationService.markRead(currentUser.requireUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
