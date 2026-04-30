package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.model.dto.CommentDto;
import com.sandbox.sandman.backend.model.dto.PageResponse;
import com.sandbox.sandman.backend.model.entity.Notification;
import com.sandbox.sandman.backend.model.entity.Post;
import com.sandbox.sandman.backend.model.entity.PostComment;
import com.sandbox.sandman.backend.repositories.PostCommentRepository;
import com.sandbox.sandman.backend.repositories.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final PostCommentRepository commentRepository;
    private final PostRepository postRepository;
    private final NotificationService notificationService;
    private final UserMapper userMapper;

    @Transactional
    public CommentDto add(Long userId, Long postId, String content) {
        Post p = postRepository.findActiveById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));
        PostComment c = new PostComment();
        c.setPostId(postId);
        c.setAuthorId(userId);
        c.setContent(content);
        PostComment saved = commentRepository.save(c);
        notificationService.push(p.getAuthorId(), Notification.TYPE_POST_COMMENT,
                userId, postId, "post", "commented on your post");
        return toDto(saved);
    }

    @Transactional
    public CommentDto edit(Long userId, Long commentId, String content) {
        PostComment c = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found"));
        if (!c.getAuthorId().equals(userId)) {
            throw new IllegalStateException("Cannot edit someone else's comment");
        }
        c.setContent(content);
        c.setEditedAt(ZonedDateTime.now());
        return toDto(commentRepository.save(c));
    }

    @Transactional
    public void delete(Long userId, Long commentId) {
        PostComment c = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found"));
        if (!c.getAuthorId().equals(userId)) {
            throw new IllegalStateException("Cannot delete someone else's comment");
        }
        c.setDeletedAt(ZonedDateTime.now());
        commentRepository.save(c);
    }

    @Transactional(readOnly = true)
    public PageResponse<CommentDto> list(Long postId, Long beforeId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        var rows = commentRepository.findByPost(postId, beforeId, PageRequest.of(0, safeLimit + 1));
        boolean hasMore = rows.size() > safeLimit;
        List<PostComment> page = hasMore ? rows.subList(0, safeLimit) : rows;
        Long nextCursor = hasMore ? page.get(page.size() - 1).getId() : null;
        return new PageResponse<>(page.stream().map(this::toDto).toList(), hasMore, nextCursor);
    }

    private CommentDto toDto(PostComment c) {
        return CommentDto.builder()
                .id(c.getId())
                .postId(c.getPostId())
                .author(userMapper.loadOne(c.getAuthorId()))
                .content(c.getContent())
                .createdAt(c.getCreatedAt())
                .editedAt(c.getEditedAt())
                .build();
    }
}
