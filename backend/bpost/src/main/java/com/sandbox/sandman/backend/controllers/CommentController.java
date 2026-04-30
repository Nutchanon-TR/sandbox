package com.sandbox.sandman.backend.controllers;

import com.sandbox.sandman.backend.model.dto.CommentCreateRequest;
import com.sandbox.sandman.backend.model.dto.CommentDto;
import com.sandbox.sandman.backend.model.dto.PageResponse;
import com.sandbox.sandman.backend.security.CurrentUser;
import com.sandbox.sandman.backend.services.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${app.api.prefix.b-post}")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;
    private final CurrentUser currentUser;

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<CommentDto> add(@PathVariable Long postId, @Valid @RequestBody CommentCreateRequest req) {
        return ResponseEntity.ok(commentService.add(currentUser.requireUserId(), postId, req.getContent()));
    }

    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<PageResponse<CommentDto>> list(
            @PathVariable Long postId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "30") int limit) {
        return ResponseEntity.ok(commentService.list(postId, beforeId, limit));
    }

    @PatchMapping("/comments/{commentId}")
    public ResponseEntity<CommentDto> edit(@PathVariable Long commentId, @Valid @RequestBody CommentCreateRequest req) {
        return ResponseEntity.ok(commentService.edit(currentUser.requireUserId(), commentId, req.getContent()));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> delete(@PathVariable Long commentId) {
        commentService.delete(currentUser.requireUserId(), commentId);
        return ResponseEntity.noContent().build();
    }
}
