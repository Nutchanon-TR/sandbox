package com.sandbox.sandman.backend.services.MessageService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudflareImageService {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.cloudflare.account-id:}")
    private String accountId;

    @Value("${app.cloudflare.api-token:}")
    private String apiToken;

    @Value("${app.cloudflare.image-model:@cf/black-forest-labs/flux-1-schnell}")
    private String imageModel;

    @Value("${app.cloudflare.image-generation-enabled:false}")
    private boolean imageGenerationEnabled;

    public GeneratedImage generate(String prompt) {
        if (!imageGenerationEnabled) {
            throw new IllegalStateException("Image generation is disabled");
        }
        if (isBlank(accountId) || isBlank(apiToken)) {
            throw new IllegalStateException("Cloudflare image generation is not configured");
        }

        String url = "https://api.cloudflare.com/client/v4/accounts/" + accountId.trim()
                + "/ai/run/" + imageModel.trim();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiToken.trim());
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", trimPrompt(prompt));
        body.put("steps", 4);

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Cloudflare image generation failed");
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            boolean success = !root.has("success") || root.path("success").asBoolean(false);
            if (!success) {
                log.warn("Cloudflare image generation returned errors: {}", root.path("errors"));
                throw new IllegalStateException("Cloudflare image generation returned an error");
            }

            JsonNode imageNode = root.path("result").path("image");
            if (imageNode.isMissingNode() || imageNode.isNull()) {
                imageNode = root.path("image");
            }
            if (imageNode.isMissingNode() || imageNode.isNull()) {
                throw new IllegalStateException("Cloudflare image response has no image payload");
            }

            String base64 = stripDataUrlPrefix(imageNode.asText());
            byte[] bytes = Base64.getDecoder().decode(base64);
            return new GeneratedImage(bytes, MediaType.IMAGE_PNG_VALUE, imageModel);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Cloudflare image response could not be parsed", e);
        }
    }

    private String trimPrompt(String prompt) {
        if (prompt == null) return "";
        String trimmed = prompt.trim();
        return trimmed.length() <= 1800 ? trimmed : trimmed.substring(0, 1800);
    }

    private String stripDataUrlPrefix(String value) {
        int commaIndex = value.indexOf(',');
        if (value.startsWith("data:") && commaIndex >= 0) {
            return value.substring(commaIndex + 1);
        }
        return value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record GeneratedImage(byte[] bytes, String mimeType, String model) {}
}
