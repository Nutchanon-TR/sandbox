package com.sandbox.sandman.backend.repositories.SyncHubRepository.CardRepository;

import com.sandbox.sandman.backend.model.entity.MessageEntity.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {

    boolean existsByUserIdAndCommentId(Long userId, Long commentId);

    long deleteByUserIdAndCommentId(Long userId, Long commentId);

    long countByCommentId(Long commentId);

    List<CommentLike> findByCommentIdIn(Collection<Long> commentIds);

    void deleteByCommentId(Long commentId);
}
