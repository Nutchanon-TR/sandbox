package com.sandbox.sandman.backend.repositories.ChatRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sandbox.sandman.backend.model.entity.ChatEntity.AiContext;

import java.util.Optional;

@Repository
public interface AiContextRepository extends JpaRepository<AiContext, Long> {
    Optional<AiContext> findByUserId(Long userId);
}
