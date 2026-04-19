package com.sandbox.sandman.backend.services.SyncHubService.CardService;

import com.sandbox.sandman.backend.model.dto.SyncHubDto.CommentCreateRequestDto;
import com.sandbox.sandman.backend.model.dto.SyncHubDto.CommentDto;
import com.sandbox.sandman.backend.model.entity.MessageEntity.Comment;
import com.sandbox.sandman.backend.model.entity.MessageEntity.User;
import com.sandbox.sandman.backend.repositories.MessageRepository.UserRepository;
import com.sandbox.sandman.backend.repositories.SyncHubRepository.CardRepository.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;

    @Transactional
    public CommentDto addComment(Long aiId, CommentCreateRequestDto request) {
        Comment comment = new Comment();
        comment.setAiId(aiId);
        comment.setUserId(request.getUserId());
        comment.setContent(request.getContent());
        Comment saved = commentRepository.save(comment);
        String displayName = userRepository.findById(saved.getUserId())
                .map(User::getDisplayName)
                .orElse("Unknown");
        return toDto(saved, displayName);
    }

    public List<CommentDto> listComments(Long aiId) {
        List<Comment> comments = commentRepository.findByAiIdOrderByCreatedAtDesc(aiId);
        Set<Long> userIds = comments.stream().map(Comment::getUserId).collect(Collectors.toSet());
        Map<Long, String> nameById = new HashMap<>();
        userRepository.findAllById(userIds).forEach(u -> nameById.put(u.getId(), u.getDisplayName()));
        return comments.stream()
                .map(c -> toDto(c, nameById.getOrDefault(c.getUserId(), "Unknown")))
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteComment(Long commentId) {
        commentRepository.deleteById(commentId);
    }

    private CommentDto toDto(Comment c, String displayName) {
        return new CommentDto(
                c.getId(),
                c.getUserId(),
                displayName,
                c.getAiId(),
                c.getContent(),
                c.getCreatedAt()
        );
    }
}
