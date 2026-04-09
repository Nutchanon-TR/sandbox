package com.sandbox.sandman.backend.repositories.ChatRepository;

import com.sandbox.sandman.backend.model.entity.ChatEntity.MessageEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageEmbeddingRepository extends JpaRepository<MessageEmbedding, Long> {
}
