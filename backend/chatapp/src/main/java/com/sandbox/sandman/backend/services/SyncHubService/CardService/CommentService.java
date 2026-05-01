package com.sandbox.sandman.backend.services.SyncHubService.CardService;

import com.sandbox.sandman.backend.model.dto.SyncHubDto.CommentCreateRequestDto;
import com.sandbox.sandman.backend.model.dto.SyncHubDto.CommentDto;
import com.sandbox.sandman.backend.model.entity.MessageEntity.Comment;
import com.sandbox.sandman.backend.model.entity.MessageEntity.CommentLike;
import com.sandbox.sandman.backend.model.entity.MessageEntity.User;
import com.sandbox.sandman.backend.repositories.MessageRepository.UserRepository;
import com.sandbox.sandman.backend.repositories.SyncHubRepository.CardRepository.CommentLikeRepository;
import com.sandbox.sandman.backend.repositories.SyncHubRepository.CardRepository.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final UserRepository userRepository;

    @Transactional
    public CommentDto addComment(Long callerId, Long aiId, CommentCreateRequestDto request) {
        Comment comment = new Comment();
        comment.setAiId(aiId);
        comment.setUserId(callerId);
        comment.setContent(request.getContent());
        comment.setStar(request.getStar() == null ? 0 : request.getStar());
        Comment saved = commentRepository.save(comment);
        String displayName = userRepository.findById(saved.getUserId())
                .map(User::getDisplayName)
                .orElse("Unknown");
        return toDto(saved, displayName, 0L);
    }

    public List<CommentDto> listComments(Long aiId) {
        List<Comment> comments = commentRepository.findByAiIdOrderByCreatedAtDesc(aiId);
        Set<Long> userIds = comments.stream().map(Comment::getUserId).collect(Collectors.toSet());
        Map<Long, String> nameById = new HashMap<>();
        userRepository.findAllById(userIds).forEach(u -> nameById.put(u.getId(), u.getDisplayName()));

        Set<Long> commentIds = comments.stream().map(Comment::getId).collect(Collectors.toSet());
        Map<Long, Long> likeCountByCommentId = commentIds.isEmpty()
                ? Map.of()
                : commentLikeRepository.findByCommentIdIn(commentIds).stream()
                        .collect(Collectors.groupingBy(CommentLike::getCommentId, Collectors.counting()));

        return comments.stream()
                .map(c -> toDto(
                        c,
                        nameById.getOrDefault(c.getUserId(), "Unknown"),
                        likeCountByCommentId.getOrDefault(c.getId(), 0L)))
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));
        if (!comment.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot delete another user's comment");
        }
        commentLikeRepository.deleteByCommentId(commentId);
        commentRepository.deleteById(commentId);
    }

    @Transactional
    public void likeComment(Long commentId, Long userId) {
        if (!commentRepository.existsById(commentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found");
        }
        if (commentLikeRepository.existsByUserIdAndCommentId(userId, commentId)) return;
        CommentLike like = new CommentLike();
        like.setUserId(userId);
        like.setCommentId(commentId);
        commentLikeRepository.save(like);
    }

    @Transactional
    public void unlikeComment(Long commentId, Long userId) {
        commentLikeRepository.deleteByUserIdAndCommentId(userId, commentId);
    }

    private CommentDto toDto(Comment c, String displayName, Long likeCount) {
        return new CommentDto(
                c.getId(),
                c.getUserId(),
                displayName,
                c.getAiId(),
                c.getContent(),
                c.getStar(),
                likeCount,
                c.getCreatedAt()
        );
    }
}
