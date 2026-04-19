package com.sandbox.sandman.backend.repositories.MessageRepository;

import com.sandbox.sandman.backend.model.entity.MessageEntity.ChatEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmbeddingRepository extends JpaRepository<ChatEmbedding, Long>, EmbeddingCustomRepository {
}
