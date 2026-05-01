package com.sandbox.sandman.backend.controllers.SyncHubController.CardController;

import com.sandbox.sandman.backend.commonauth.CurrentUser;
import com.sandbox.sandman.backend.services.SyncHubService.CardService.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${app.api.prefix.chat-app}")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;
    private final CurrentUser currentUser;

    @PostMapping("/ai/{aiId}/like")
    public ResponseEntity<Void> like(@PathVariable Long aiId) {
        likeService.like(currentUser.requireUserId(), aiId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/ai/{aiId}/like")
    public ResponseEntity<Void> unlike(@PathVariable Long aiId) {
        likeService.unlike(currentUser.requireUserId(), aiId);
        return ResponseEntity.ok().build();
    }
}
