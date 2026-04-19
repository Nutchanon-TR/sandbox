package com.sandbox.sandman.backend.model.entity.MessageEntity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    @Column(name = "role", columnDefinition = "TEXT")
    private String role;

    // "character" is a reserved keyword in Postgres — quote via backticks so Hibernate escapes it
    @Column(name = "`character`", columnDefinition = "TEXT")
    private String character;

    @Column(name = "biography", columnDefinition = "TEXT")
    private String biography;

    @Column(name = "rule", columnDefinition = "TEXT")
    private String rule;

    @Column(name = "poster_url", columnDefinition = "TEXT")
    private String posterUrl;

    public String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        appendSection(sb, "ROLE", role);
        appendSection(sb, "CHARACTER", character);
        appendSection(sb, "BIOGRAPHY", biography);
        appendSection(sb, "RULE", rule);
        return sb.toString().trim();
    }

    private static void appendSection(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) return;
        if (sb.length() > 0) sb.append("\n\n");
        sb.append("[").append(label).append("]\n").append(value);
    }
}
