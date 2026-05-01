package com.sandbox.sandman.backend.controllers;

import com.sandbox.sandman.backend.model.dto.PageResponse;
import com.sandbox.sandman.backend.model.dto.PostCreateRequest;
import com.sandbox.sandman.backend.model.dto.PostDto;
import com.sandbox.sandman.backend.model.dto.PostUpdateRequest;
import com.sandbox.sandman.backend.commonauth.CurrentUser;
import com.sandbox.sandman.backend.services.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${app.api.prefix.b-post}/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final CurrentUser currentUser;

    @PostMapping
    public ResponseEntity<PostDto> create(@RequestBody PostCreateRequest req) {
        return ResponseEntity.ok(postService.create(currentUser.requireUserId(), req));
    }

    @GetMapping("/feed")
    public ResponseEntity<PageResponse<PostDto>> feed(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(postService.feed(currentUser.requireUserId(), beforeId, limit));
    }

    @GetMapping("/by-author/{authorId}")
    public ResponseEntity<PageResponse<PostDto>> byAuthor(
            @PathVariable Long authorId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(postService.byAuthor(currentUser.requireUserId(), authorId, beforeId, limit));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<PostDto> get(@PathVariable Long postId) {
        return ResponseEntity.ok(postService.get(currentUser.requireUserId(), postId));
    }

    @PatchMapping("/{postId}")
    public ResponseEntity<PostDto> update(@PathVariable Long postId, @RequestBody PostUpdateRequest req) {
        return ResponseEntity.ok(postService.update(currentUser.requireUserId(), postId, req));
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> delete(@PathVariable Long postId) {
        postService.softDelete(currentUser.requireUserId(), postId);
        return ResponseEntity.noContent().build();
    }
}
