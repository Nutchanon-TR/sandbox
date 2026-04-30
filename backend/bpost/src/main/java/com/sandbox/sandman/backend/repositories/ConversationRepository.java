package com.sandbox.sandman.backend.repositories;

import com.sandbox.sandman.backend.model.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByUserAIdAndUserBId(Long userAId, Long userBId);

    @Query("""
        SELECT c FROM Conversation c
        WHERE c.userAId = :userId OR c.userBId = :userId
        ORDER BY c.lastMessageAt DESC NULLS LAST, c.id DESC
        """)
    List<Conversation> findByUser(@Param("userId") Long userId);
}
