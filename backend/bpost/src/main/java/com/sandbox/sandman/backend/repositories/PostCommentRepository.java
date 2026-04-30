package com.sandbox.sandman.backend.repositories;

import com.sandbox.sandman.backend.model.entity.PostComment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostCommentRepository extends JpaRepository<PostComment, Long> {

    @Query("""
        SELECT c FROM PostComment c
        WHERE c.postId = :postId AND c.deletedAt IS NULL
          AND (:beforeId IS NULL OR c.id < :beforeId)
        ORDER BY c.id DESC
        """)
    List<PostComment> findByPost(
            @Param("postId") Long postId,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    long countByPostIdAndDeletedAtIsNull(Long postId);
}
