package com.sandbox.sandman.backend.repositories.MessageRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sandbox.sandman.backend.model.entity.MessageEntity.AiContext;

@Repository
public interface AiContextRepository extends JpaRepository<AiContext, Long> {
}
