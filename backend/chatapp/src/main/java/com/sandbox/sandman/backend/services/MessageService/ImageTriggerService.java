package com.sandbox.sandman.backend.services.MessageService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sandbox.sandman.backend.model.entity.MessageEntity.AiContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ImageTriggerService {

    private static final List<String> DEFAULT_PHOTO_KEYWORDS = List.of(
            "ถ่ายรูป",
            "ส่งรูป",
            "ขอดูรูป",
            "ถ่ายมาให้ดู"
    );

    private static final List<String> DEFAULT_ACTIVITY_KEYWORDS = List.of(
            "ทำอะไรอยู่",
            "ตอนนี้ทำไร",
            "อยู่ไหน",
            "ทำอะไรตอนนี้"
    );

    private final ObjectMapper objectMapper;

    public ImageTriggerDecision detect(String message, AiContext aiContext) {
        if (message == null || message.isBlank()) {
            return ImageTriggerDecision.none();
        }
        if (aiContext != null && !Boolean.TRUE.equals(aiContext.getImageEnabled())) {
            return ImageTriggerDecision.none();
        }

        TriggerRules rules = parseRules(aiContext);
        String normalized = normalize(message);
        boolean hasPhotoKeyword = rules.photoKeywords().stream().anyMatch(normalized::contains);
        if (!hasPhotoKeyword) {
            return ImageTriggerDecision.none();
        }

        boolean hasActivityIntent = rules.activityKeywords().stream().anyMatch(normalized::contains);
        return new ImageTriggerDecision(
                hasActivityIntent ? ImageTriggerType.FIRST_PERSON_SNAPSHOT : ImageTriggerType.GENERAL_IMAGE
        );
    }

    private TriggerRules parseRules(AiContext aiContext) {
        if (aiContext == null || aiContext.getImageTriggerRules() == null || aiContext.getImageTriggerRules().isBlank()) {
            return new TriggerRules(DEFAULT_PHOTO_KEYWORDS, DEFAULT_ACTIVITY_KEYWORDS);
        }
        try {
            JsonNode root = objectMapper.readTree(aiContext.getImageTriggerRules());
            List<String> photoKeywords = readStringList(root.get("photoKeywords"), DEFAULT_PHOTO_KEYWORDS);
            List<String> activityKeywords = readStringList(root.get("activityKeywords"), DEFAULT_ACTIVITY_KEYWORDS);
            return new TriggerRules(photoKeywords, activityKeywords);
        } catch (Exception e) {
            return new TriggerRules(DEFAULT_PHOTO_KEYWORDS, DEFAULT_ACTIVITY_KEYWORDS);
        }
    }

    private List<String> readStringList(JsonNode node, List<String> fallback) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return fallback;
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                values.add(normalize(item.asText()));
            }
        }
        return values.isEmpty() ? fallback : values;
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    private record TriggerRules(List<String> photoKeywords, List<String> activityKeywords) {}

    public enum ImageTriggerType {
        NONE,
        FIRST_PERSON_SNAPSHOT,
        GENERAL_IMAGE
    }

    public record ImageTriggerDecision(ImageTriggerType type) {
        public boolean shouldGenerateImage() {
            return type != ImageTriggerType.NONE;
        }

        public static ImageTriggerDecision none() {
            return new ImageTriggerDecision(ImageTriggerType.NONE);
        }
    }
}
