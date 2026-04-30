package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.model.entity.Notification;
import com.sandbox.sandman.backend.model.entity.Post;
import com.sandbox.sandman.backend.model.entity.PostLike;
import com.sandbox.sandman.backend.repositories.PostLikeRepository;
import com.sandbox.sandman.backend.repositories.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LikeService {

    private final PostLikeRepository postLikeRepository;
    private final PostRepository postRepository;
    private final NotificationService notificationService;

    @Transactional
    public boolean like(Long userId, Long postId) {
        Post p = postRepository.findActiveById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));
        if (postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            return false;
        }
        PostLike like = new PostLike();
        like.setPostId(postId);
        like.setUserId(userId);
        postLikeRepository.save(like);
        notificationService.push(p.getAuthorId(), Notification.TYPE_POST_LIKE,
                userId, postId, "post", "liked your post");
        return true;
    }

    @Transactional
    public void unlike(Long userId, Long postId) {
        postLikeRepository.deleteByPostIdAndUserId(postId, userId);
    }
}
