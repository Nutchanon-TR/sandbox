package com.sandbox.sandman.backend.controllers.SyncHubController.CardController;

import com.sandbox.sandman.backend.model.dto.SyncHubDto.CommentCreateRequestDto;
import com.sandbox.sandman.backend.model.dto.SyncHubDto.CommentDto;
import com.sandbox.sandman.backend.services.SyncHubService.CardService.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("${app.api.prefix.chat-app}")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/ai/{aiId}/comments")
    public ResponseEntity<CommentDto> addComment(
            @PathVariable Long aiId,
            @RequestBody CommentCreateRequestDto request) {
        return ResponseEntity.ok(commentService.addComment(aiId, request));
    }

    @GetMapping("/ai/{aiId}/comments")
    public ResponseEntity<List<CommentDto>> listComments(@PathVariable Long aiId) {
        return ResponseEntity.ok(commentService.listComments(aiId));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long commentId,
            @RequestParam Long userId) {
        commentService.deleteComment(commentId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/comments/{commentId}/like/{userId}")
    public ResponseEntity<Void> likeComment(@PathVariable Long commentId, @PathVariable Long userId) {
        commentService.likeComment(commentId, userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/comments/{commentId}/like/{userId}")
    public ResponseEntity<Void> unlikeComment(@PathVariable Long commentId, @PathVariable Long userId) {
        commentService.unlikeComment(commentId, userId);
        return ResponseEntity.ok().build();
    }
}
