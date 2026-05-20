package com.sandbox.sandman.backend.services.MessageService;

import com.fasterxml.jackson.databind.JsonNode;
import com.sandbox.sandman.backend.model.entity.MessageEntity.Chat;
import com.sandbox.sandman.backend.repositories.MessageRepository.EmbeddingRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.*;

@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final String HF_API_URL = "https://router.huggingface.co/hf-inference/models/intfloat/multilingual-e5-small/pipeline/feature-extraction";

    private final EmbeddingRepository messageEmbeddingRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.huggingface.api-key:}")
    private String hfApiKey;

    public void embedAndSave(Chat chat) {
        try {
            float[] embedding = embed("passage: " + chat.getContent());
            messageEmbeddingRepository.saveEmbedding(chat.getId(), toVectorString(embedding));
        } catch (Exception e) {
            log.warn("Failed to embed message {}: {}", chat.getId(), e.getMessage());
        }
    }

    public List<Long> searchSimilarMessages(String query, Long roomId, int limit) {
        try {
            float[] queryVector = embed("query: " + query);
            return messageEmbeddingRepository.searchSimilarMessages(roomId, toVectorString(queryVector), limit);
        } catch (Exception e) {
            log.warn("Failed to search similar messages: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private float[] embed(String text) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (hfApiKey != null && !hfApiKey.isBlank()) {
            headers.setBearerAuth(hfApiKey);
        }

        Map<String, Object> body = Map.of("inputs", text);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                HF_API_URL, HttpMethod.POST, request, JsonNode.class);

        JsonNode responseBody = response.getBody();
        JsonNode vectorNode = extractVectorNode(responseBody);
        if (vectorNode == null || vectorNode.isEmpty()) {
            throw new RuntimeException("Empty response from HuggingFace API");
        }

        float[] result = new float[vectorNode.size()];
        for (int i = 0; i < vectorNode.size(); i++) {
            result[i] = (float) vectorNode.get(i).asDouble();
        }
        return result;
    }

    private JsonNode extractVectorNode(JsonNode responseBody) {
        if (responseBody == null || !responseBody.isArray() || responseBody.isEmpty()) {
            return null;
        }

        JsonNode first = responseBody.get(0);
        if (first != null && first.isNumber()) {
            return responseBody;
        }
        if (first != null && first.isArray()) {
            return first;
        }
        return null;
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
