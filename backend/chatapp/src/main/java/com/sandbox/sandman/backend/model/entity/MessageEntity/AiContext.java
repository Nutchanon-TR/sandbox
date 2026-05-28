package com.sandbox.sandman.backend.model.entity.MessageEntity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;

import java.time.LocalTime;
import java.time.ZonedDateTime;

@Entity
@Table(name = "ai_context", schema = "chat_app")
@Data
@NoArgsConstructor
public class AiContext {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ai_name", nullable = false, length = 100)
    private String aiName = "AI Assistant";

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Column(name = "appearance_reference_url", columnDefinition = "TEXT")
    private String appearanceReferenceUrl;

    @Column(name = "appearance_reference_object_path", columnDefinition = "TEXT")
    private String appearanceReferenceObjectPath;

    @Column(name = "role", columnDefinition = "TEXT")
    private String role;

    // "character" is a reserved keyword in Postgres — quote via backticks so Hibernate escapes it
    @Column(name = "`character`", columnDefinition = "TEXT")
    private String character;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "personality_traits", columnDefinition = "jsonb", nullable = false)
    private String personalityTraits = "[]";

    @Column(name = "biography", columnDefinition = "TEXT")
    private String biography;

    @Column(name = "speech_style", columnDefinition = "TEXT")
    private String speechStyle;

    @Column(name = "relationship_context", columnDefinition = "TEXT")
    private String relationshipContext;

    @Column(name = "memory_notes", columnDefinition = "TEXT")
    private String memoryNotes;

    @Column(name = "response_boundaries", columnDefinition = "TEXT")
    private String responseBoundaries;

    @Column(name = "system_context", columnDefinition = "TEXT")
    private String systemContext;

    @Column(name = "rule", columnDefinition = "TEXT")
    private String rule;

    @Column(name = "poster_url", columnDefinition = "TEXT")
    private String posterUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @Column(name = "visibility", length = 20, nullable = false)
    private String visibility = "private";

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "style_examples", columnDefinition = "jsonb", nullable = false)
    private String styleExamples = "[]";

    @Column(name = "image_enabled", nullable = false)
    private Boolean imageEnabled = true;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "image_trigger_rules", columnDefinition = "jsonb", nullable = false)
    private String imageTriggerRules = """
            {
              "photoKeywords": ["ถ่ายรูป", "ส่งรูป", "ขอดูรูป", "ถ่ายมาให้ดู"],
              "activityKeywords": ["ทำอะไรอยู่", "ตอนนี้ทำไร", "อยู่ไหน", "ทำอะไรตอนนี้"]
            }
            """;

    @Column(name = "image_prompt_template", columnDefinition = "TEXT")
    private String imagePromptTemplate;

    @Column(name = "fine_tune_status", length = 32, nullable = false)
    private String fineTuneStatus = "not_started";

    @Column(name = "fine_tuned_model_id", columnDefinition = "TEXT")
    private String fineTunedModelId;

    @Column(name = "persona_feed_enabled", nullable = false)
    private Boolean personaFeedEnabled = false;

    @Column(name = "persona_feed_min_interval_hours", nullable = false)
    private Integer personaFeedMinIntervalHours = 8;

    @Column(name = "persona_feed_max_interval_hours", nullable = false)
    private Integer personaFeedMaxIntervalHours = 24;

    @Column(name = "persona_feed_window_start")
    private LocalTime personaFeedWindowStart;

    @Column(name = "persona_feed_window_end")
    private LocalTime personaFeedWindowEnd;

    @Column(name = "persona_feed_timezone", nullable = false, length = 64)
    private String personaFeedTimezone = "Asia/Bangkok";

    @Column(name = "persona_feed_next_post_at")
    private ZonedDateTime personaFeedNextPostAt;

    @Column(name = "persona_feed_last_post_at")
    private ZonedDateTime personaFeedLastPostAt;

    public String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        appendSection(sb, "IDENTITY", buildIdentityPrompt());
        appendSection(sb, "ROLE", role);
        appendSection(sb, "CHARACTER AND PERSONALITY", character);
        appendSection(sb, "PERSONALITY TRAITS", normalizeJsonText(personalityTraits));
        appendSection(sb, "BIOGRAPHY", biography);
        appendSection(sb, "RELATIONSHIP CONTEXT", relationshipContext);
        appendSection(sb, "MEMORY NOTES", memoryNotes);
        appendSection(sb, "SPEECH STYLE", speechStyle);
        appendSection(sb, "RESPONSE BOUNDARIES AND RULES", combineSections(responseBoundaries, rule));
        appendSection(sb, "STYLE EXAMPLES", normalizeJsonText(styleExamples));
        appendSection(sb, "ADVANCED SYSTEM CONTEXT", systemContext);
        return sb.toString().trim();
    }

    private String buildIdentityPrompt() {
        String name = aiName == null || aiName.isBlank() ? "AI Assistant" : aiName.trim();
        return "You are %s. Stay in character as %s throughout the conversation.".formatted(name, name);
    }

    private static void appendSection(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) return;
        if (sb.length() > 0) sb.append("\n\n");
        sb.append("[").append(label).append("]\n").append(value);
    }

    private static String normalizeJsonText(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if ("[]".equals(trimmed.replaceAll("\\s+", ""))) return null;
        return trimmed;
    }

    private static String combineSections(String first, String second) {
        StringBuilder sb = new StringBuilder();
        appendInline(sb, first);
        appendInline(sb, second);
        return sb.length() == 0 ? null : sb.toString();
    }

    private static void appendInline(StringBuilder sb, String value) {
        if (value == null || value.isBlank()) return;
        if (sb.length() > 0) sb.append("\n\n");
        sb.append(value.trim());
    }
}
