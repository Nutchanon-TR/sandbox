package com.sandbox.sandman.backend.controllers.SyncHubController.CardController;

import com.sandbox.sandman.backend.services.SyncHubService.CardService.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${app.api.prefix.chat-app}")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    @PostMapping("/ai/{aiId}/like/{userId}")
    public ResponseEntity<Void> like(@PathVariable Long aiId, @PathVariable Long userId) {
        likeService.like(userId, aiId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/ai/{aiId}/like/{userId}")
    public ResponseEntity<Void> unlike(@PathVariable Long aiId, @PathVariable Long userId) {
        likeService.unlike(userId, aiId);
        return ResponseEntity.ok().build();
    }
}
