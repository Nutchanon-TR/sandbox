package com.sandbox.sandman.backend.repositories;

import com.sandbox.sandman.backend.model.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("""
        SELECT m FROM Message m
        WHERE m.conversationId = :conversationId
          AND (:beforeId IS NULL OR m.id < :beforeId)
        ORDER BY m.id DESC
        """)
    List<Message> findByConversation(
            @Param("conversationId") Long conversationId,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    long countByConversationIdAndSenderIdNotAndReadAtIsNull(Long conversationId, Long senderId);

    @Modifying
    @Query("""
        UPDATE Message m SET m.readAt = :ts
        WHERE m.conversationId = :conversationId
          AND m.senderId <> :readerId
          AND m.readAt IS NULL
        """)
    int markAllRead(
            @Param("conversationId") Long conversationId,
            @Param("readerId") Long readerId,
            @Param("ts") ZonedDateTime ts);
}
