package com.sandbox.sandman.backend.model.entity.PersonaFeedEntity;

import com.sandbox.sandman.backend.model.entity.MessageEntity.AiContext;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "persona_feed_posts", schema = "chat_app")
@Data
@NoArgsConstructor
public class PersonaFeedPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_context_id", nullable = false)
    private AiContext persona;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "image_urls", nullable = false, columnDefinition = "text[]")
    private List<String> imageUrls = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
    }
}
