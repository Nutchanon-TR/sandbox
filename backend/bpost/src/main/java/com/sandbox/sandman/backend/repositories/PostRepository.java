package com.sandbox.sandman.backend.repositories;

import com.sandbox.sandman.backend.model.entity.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    @Query("""
        SELECT p FROM Post p
        WHERE p.deletedAt IS NULL
          AND (p.authorId = :userId OR p.authorId IN :friendIds)
          AND (:beforeId IS NULL OR p.id < :beforeId)
        ORDER BY p.id DESC
        """)
    List<Post> findFeed(
            @Param("userId") Long userId,
            @Param("friendIds") List<Long> friendIds,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @Query("""
        SELECT p FROM Post p
        WHERE p.deletedAt IS NULL AND p.authorId = :authorId
          AND (:beforeId IS NULL OR p.id < :beforeId)
        ORDER BY p.id DESC
        """)
    List<Post> findByAuthor(
            @Param("authorId") Long authorId,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<Post> findActiveById(@Param("id") Long id);
}
