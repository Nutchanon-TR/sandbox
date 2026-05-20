package com.sandbox.sandman.backend.repositories.MessageRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sandbox.sandman.backend.model.entity.MessageEntity.AiContext;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiContextRepository extends JpaRepository<AiContext, Long> {
    @Query("""
            SELECT ai FROM AiContext ai
            WHERE ai.createdByUser.id = :userId
              AND ai.visibility <> 'archived'
            ORDER BY ai.id DESC
            """)
    List<AiContext> findActiveByOwner(@Param("userId") Long userId);

    @Query("""
            SELECT ai FROM AiContext ai
            WHERE ai.id = :id
              AND ai.createdByUser.id = :userId
              AND ai.visibility <> 'archived'
            """)
    Optional<AiContext> findActiveByIdAndOwner(@Param("id") Long id, @Param("userId") Long userId);
}
