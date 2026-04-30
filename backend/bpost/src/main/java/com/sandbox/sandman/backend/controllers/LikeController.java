package com.sandbox.sandman.backend.controllers;

import com.sandbox.sandman.backend.security.CurrentUser;
import com.sandbox.sandman.backend.services.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${app.api.prefix.b-post}/posts/{postId}/likes")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;
    private final CurrentUser currentUser;

    @PostMapping
    public ResponseEntity<Void> like(@PathVariable Long postId) {
        likeService.like(currentUser.requireUserId(), postId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> unlike(@PathVariable Long postId) {
        likeService.unlike(currentUser.requireUserId(), postId);
        return ResponseEntity.noContent().build();
    }
}
