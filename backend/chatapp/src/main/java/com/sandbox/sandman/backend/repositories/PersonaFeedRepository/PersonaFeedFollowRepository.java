package com.sandbox.sandman.backend.repositories.PersonaFeedRepository;

import com.sandbox.sandman.backend.model.entity.MessageEntity.Friend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PersonaFeedFollowRepository extends JpaRepository<Friend, Long> {
    boolean existsByUserIdAndAiId(Long userId, Long aiId);
}
