package com.sandbox.sandman.backend.services.MessageService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
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

    @Value("${app.cloudflare.reference-image-model:@cf/runwayml/stable-diffusion-v1-5-img2img}")
    private String referenceImageModel;

    @Value("${app.cloudflare.reference-image-strength:0.65}")
    private double referenceImageStrength;

    @Value("${app.cloudflare.image-generation-enabled:false}")
    private boolean imageGenerationEnabled;

    private static final int PROMPT_SAFETY_ERROR_CODE = 3030;

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

        ResponseEntity<String> response = exchange(
                url,
                new HttpEntity<>(body, headers),
                String.class,
                "Cloudflare image generation"
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Cloudflare image generation failed");
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            throwIfProviderReturnedError(root, "Cloudflare image generation");

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

    public GeneratedImage generateFromReference(String prompt, byte[] referenceImage) {
        if (!imageGenerationEnabled) {
            throw new IllegalStateException("Image generation is disabled");
        }
        if (isBlank(accountId) || isBlank(apiToken)) {
            throw new IllegalStateException("Cloudflare image generation is not configured");
        }
        if (referenceImage == null || referenceImage.length == 0) {
            throw new IllegalArgumentException("Reference image is empty");
        }

        String url = "https://api.cloudflare.com/client/v4/accounts/" + accountId.trim()
                + "/ai/run/" + referenceImageModel.trim();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiToken.trim());
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", trimPrompt(prompt));
        body.put("image_b64", Base64.getEncoder().encodeToString(referenceImage));
        body.put("num_steps", 20);
        body.put("strength", clampStrength(referenceImageStrength));

        ResponseEntity<byte[]> response = exchange(
                url,
                new HttpEntity<>(body, headers),
                byte[].class,
                "Cloudflare reference image generation"
        );
        byte[] bytes = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || bytes == null || bytes.length == 0) {
            throw new IllegalStateException("Cloudflare reference image generation failed");
        }

        MediaType contentType = response.getHeaders().getContentType();
        if (contentType != null && MediaType.APPLICATION_JSON.includes(contentType)) {
            return parseJsonImage(bytes, referenceImageModel);
        }

        String mimeType = contentType != null && contentType.getType().equals("image")
                ? contentType.toString()
                : MediaType.IMAGE_PNG_VALUE;
        return new GeneratedImage(bytes, mimeType, referenceImageModel);
    }

    private GeneratedImage parseJsonImage(byte[] bytes, String model) {
        try {
            JsonNode root = objectMapper.readTree(new String(bytes, StandardCharsets.UTF_8));
            throwIfProviderReturnedError(root, "Cloudflare reference image generation");

            JsonNode imageNode = root.path("result").path("image");
            if (imageNode.isMissingNode() || imageNode.isNull()) {
                imageNode = root.path("image");
            }
            if (imageNode.isMissingNode() || imageNode.isNull()) {
                throw new IllegalStateException("Cloudflare reference image response has no image payload");
            }

            return new GeneratedImage(
                    Base64.getDecoder().decode(stripDataUrlPrefix(imageNode.asText())),
                    MediaType.IMAGE_PNG_VALUE,
                    model
            );
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Cloudflare reference image response could not be parsed", e);
        }
    }

    private <T> ResponseEntity<T> exchange(
            String url,
            HttpEntity<?> request,
            Class<T> responseType,
            String operation) {
        try {
            return restTemplate.exchange(url, HttpMethod.POST, request, responseType);
        } catch (HttpStatusCodeException e) {
            if (isPromptSafetyRejection(e.getResponseBodyAsString())) {
                throw new PromptSafetyRejectedException(operation + " rejected the image prompt for safety", e);
            }
            throw new IllegalStateException(
                    "%s request failed with status %s".formatted(operation, e.getStatusCode().value()),
                    e
            );
        }
    }

    private void throwIfProviderReturnedError(JsonNode root, String operation) {
        boolean success = !root.has("success") || root.path("success").asBoolean(false);
        if (success) {
            return;
        }

        JsonNode errors = root.path("errors");
        if (hasPromptSafetyRejection(errors)) {
            throw new PromptSafetyRejectedException(operation + " rejected the image prompt for safety");
        }

        log.warn("{} returned errors: {}", operation, errors);
        throw new IllegalStateException(operation + " returned an error");
    }

    private boolean isPromptSafetyRejection(String responseBody) {
        if (isBlank(responseBody)) {
            return false;
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (hasPromptSafetyRejection(root.path("errors"))) {
                return true;
            }
        } catch (Exception ignored) {
            // Fall back to a narrow text check when Cloudflare returns a non-JSON error body.
        }

        String normalized = responseBody.toLowerCase(Locale.ROOT);
        return normalized.contains("\"code\":" + PROMPT_SAFETY_ERROR_CODE)
                || normalized.contains("input prompt contains nsfw content");
    }

    private boolean hasPromptSafetyRejection(JsonNode errors) {
        if (errors == null || !errors.isArray()) {
            return false;
        }

        for (JsonNode error : errors) {
            if (error.path("code").asInt(-1) == PROMPT_SAFETY_ERROR_CODE) {
                return true;
            }

            String message = error.path("message").asText("").toLowerCase(Locale.ROOT);
            if (message.contains("input prompt contains nsfw content")) {
                return true;
            }
        }
        return false;
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

    private double clampStrength(double strength) {
        return Math.max(0, Math.min(1, strength));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static class PromptSafetyRejectedException extends RuntimeException {
        public PromptSafetyRejectedException(String message) {
            super(message);
        }

        public PromptSafetyRejectedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public record GeneratedImage(byte[] bytes, String mimeType, String model) {}
}
