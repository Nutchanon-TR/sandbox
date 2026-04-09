package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.model.entity.ChatEntity.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final String HF_API_URL = "https://api-inference.huggingface.co/pipeline/feature-extraction/intfloat/multilingual-e5-small";

    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate;

    @Value("${app.huggingface.api-key:}")
    private String hfApiKey;

    public EmbeddingService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Embed a message and save to message_embeddings table.
     * Uses "passage: " prefix as required by E5 models.
     */
    public void embedAndSave(Message message) {
        try {
            float[] embedding = embed("passage: " + message.getContent());
            String vectorStr = toVectorString(embedding);
            jdbcTemplate.update(
                    "INSERT INTO chat.message_embeddings (message_id, embedding) VALUES (?, ?::vector) " +
                    "ON CONFLICT (message_id) DO UPDATE SET embedding = EXCLUDED.embedding",
                    message.getId(), vectorStr);
        } catch (Exception e) {
            log.warn("Failed to embed message {}: {}", message.getId(), e.getMessage());
        }
    }

    /**
     * Search for the most similar messages in a room using cosine similarity.
     * Uses "query: " prefix as required by E5 models.
     * Returns message IDs ordered by similarity.
     */
    public List<Long> searchSimilarMessages(String query, Long roomId, int limit) {
        try {
            float[] queryVector = embed("query: " + query);
            String vectorStr = toVectorString(queryVector);
            return jdbcTemplate.queryForList(
                    """
                    SELECT me.message_id
                    FROM chat.message_embeddings me
                    JOIN chat.messages m ON m.id = me.message_id
                    WHERE m.room_id = ?
                    ORDER BY me.embedding <=> ?::vector
                    LIMIT ?
                    """,
                    Long.class,
                    roomId, vectorStr, limit);
        } catch (Exception e) {
            log.warn("Failed to search similar messages: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Call HuggingFace Inference API for multilingual-e5-small.
     */
    private float[] embed(String text) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (hfApiKey != null && !hfApiKey.isBlank()) {
            headers.setBearerAuth(hfApiKey);
        }

        Map<String, Object> body = Map.of("inputs", text);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<double[]> response = restTemplate.exchange(
                HF_API_URL, HttpMethod.POST, request, double[].class);

        double[] doubles = response.getBody();
        if (doubles == null) {
            throw new RuntimeException("Empty response from HuggingFace API");
        }

        float[] result = new float[doubles.length];
        for (int i = 0; i < doubles.length; i++) {
            result[i] = (float) doubles[i];
        }
        return result;
    }

    private String toVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
