package com.sandbox.sandman.backend.services.MessageService;

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

        // HuggingFace feature-extraction returns nested array [[...]] not flat array [...]
        ResponseEntity<double[][]> response = restTemplate.exchange(
                HF_API_URL, HttpMethod.POST, request, double[][].class);

        double[][] responseBody = response.getBody();
        if (responseBody == null || responseBody.length == 0 || responseBody[0].length == 0) {
            throw new RuntimeException("Empty response from HuggingFace API");
        }
        double[] doubles = responseBody[0];
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
