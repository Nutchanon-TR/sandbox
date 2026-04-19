package com.sandbox.sandman.backend.repositories.MessageRepository;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

public class EmbeddingCustomRepositoryImpl implements EmbeddingCustomRepository {

    private final JdbcTemplate jdbcTemplate;

    public EmbeddingCustomRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveEmbedding(Long messageId, String vectorStr) {
        jdbcTemplate.update(
                "INSERT INTO chat_app.chat_embeddings (message_id, embedding) VALUES (?, ?::vector) " +
                "ON CONFLICT (message_id) DO UPDATE SET embedding = EXCLUDED.embedding",
                messageId, vectorStr);
    }

    @Override
    public List<Long> searchSimilarMessages(Long roomId, String vectorStr, int limit) {
        return jdbcTemplate.queryForList(
                """
                SELECT me.message_id
                FROM chat_app.chat_embeddings me
                JOIN chat_app.chats m ON m.id = me.message_id
                WHERE m.room_id = ?
                ORDER BY me.embedding <=> ?::vector
                LIMIT ?
                """,
                Long.class,
                roomId, vectorStr, limit);
    }
}
