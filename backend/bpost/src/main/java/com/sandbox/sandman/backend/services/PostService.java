package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.model.dto.PageResponse;
import com.sandbox.sandman.backend.model.dto.PostCreateRequest;
import com.sandbox.sandman.backend.model.dto.PostDto;
import com.sandbox.sandman.backend.model.dto.PostUpdateRequest;
import com.sandbox.sandman.backend.model.dto.UserSummaryDto;
import com.sandbox.sandman.backend.model.entity.Post;
import com.sandbox.sandman.backend.repositories.PostCommentRepository;
import com.sandbox.sandman.backend.repositories.PostLikeRepository;
import com.sandbox.sandman.backend.repositories.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostCommentRepository commentRepository;
    private final FriendService friendService;
    private final UserMapper userMapper;

    @Transactional
    public PostDto create(Long authorId, PostCreateRequest req) {
        Post p = new Post();
        p.setAuthorId(authorId);
        p.setContent(req.getContent() == null ? "" : req.getContent());
        p.setImageUrls(req.getImageUrls() == null ? List.of() : req.getImageUrls());
        p.setVisibility(req.getVisibility() == null ? "FRIENDS" : req.getVisibility());
        Post saved = postRepository.save(p);
        return toDto(saved, authorId);
    }

    @Transactional
    public PostDto update(Long userId, Long postId, PostUpdateRequest req) {
        Post p = postRepository.findActiveById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));
        if (!p.getAuthorId().equals(userId)) {
            throw new IllegalStateException("Cannot edit someone else's post");
        }
        if (req.getContent() != null) p.setContent(req.getContent());
        if (req.getImageUrls() != null) p.setImageUrls(req.getImageUrls());
        if (req.getVisibility() != null) p.setVisibility(req.getVisibility());
        p.setEditedAt(ZonedDateTime.now());
        return toDto(postRepository.save(p), userId);
    }

    @Transactional
    public void softDelete(Long userId, Long postId) {
        Post p = postRepository.findActiveById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));
        if (!p.getAuthorId().equals(userId)) {
            throw new IllegalStateException("Cannot delete someone else's post");
        }
        p.setDeletedAt(ZonedDateTime.now());
        postRepository.save(p);
    }

    @Transactional(readOnly = true)
    public PageResponse<PostDto> feed(Long userId, Long beforeId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        List<Long> friendIds = friendService.friendIdsOf(userId);
        if (friendIds.isEmpty()) friendIds = List.of(-1L); // JPA IN cannot be empty
        var rows = postRepository.findFeed(userId, friendIds, beforeId, PageRequest.of(0, safeLimit + 1));
        return enrich(rows, safeLimit, userId);
    }

    @Transactional(readOnly = true)
    public PageResponse<PostDto> byAuthor(Long viewerId, Long authorId, Long beforeId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        var rows = postRepository.findByAuthor(authorId, beforeId, PageRequest.of(0, safeLimit + 1));
        return enrich(rows, safeLimit, viewerId);
    }

    @Transactional(readOnly = true)
    public PostDto get(Long viewerId, Long postId) {
        Post p = postRepository.findActiveById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));
        return toDto(p, viewerId);
    }

    private PageResponse<PostDto> enrich(List<Post> rows, int limit, Long viewerId) {
        boolean hasMore = rows.size() > limit;
        List<Post> page = hasMore ? rows.subList(0, limit) : rows;
        if (page.isEmpty()) return new PageResponse<>(List.of(), false, null);

        List<Long> ids = page.stream().map(Post::getId).toList();
        Set<Long> authorIds = new HashSet<>();
        page.forEach(p -> authorIds.add(p.getAuthorId()));
        Map<Long, UserSummaryDto> authors = userMapper.loadAll(new ArrayList<>(authorIds));
        Set<Long> liked = postLikeRepository.findLikedPostIds(viewerId, ids);

        List<PostDto> dtos = new ArrayList<>(page.size());
        for (Post p : page) {
            dtos.add(PostDto.builder()
                    .id(p.getId())
                    .author(authors.get(p.getAuthorId()))
                    .content(p.getContent())
                    .imageUrls(p.getImageUrls())
                    .visibility(p.getVisibility())
                    .createdAt(p.getCreatedAt())
                    .editedAt(p.getEditedAt())
                    .likeCount(postLikeRepository.countByPostId(p.getId()))
                    .commentCount(commentRepository.countByPostIdAndDeletedAtIsNull(p.getId()))
                    .likedByMe(liked.contains(p.getId()))
                    .build());
        }
        Long nextCursor = hasMore ? page.get(page.size() - 1).getId() : null;
        return new PageResponse<>(dtos, hasMore, nextCursor);
    }

    private PostDto toDto(Post p, Long viewerId) {
        return PostDto.builder()
                .id(p.getId())
                .author(userMapper.loadOne(p.getAuthorId()))
                .content(p.getContent())
                .imageUrls(p.getImageUrls())
                .visibility(p.getVisibility())
                .createdAt(p.getCreatedAt())
                .editedAt(p.getEditedAt())
                .likeCount(postLikeRepository.countByPostId(p.getId()))
                .commentCount(commentRepository.countByPostIdAndDeletedAtIsNull(p.getId()))
                .likedByMe(viewerId != null && postLikeRepository.existsByPostIdAndUserId(p.getId(), viewerId))
                .build();
    }
}
