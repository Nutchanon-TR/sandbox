package com.sandbox.sandman.backend.model.entity.MessageEntity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;

import java.time.ZonedDateTime;

@Entity
@Table(name = "chat_attachments", schema = "chat_app")
@Data
@NoArgsConstructor
public class ChatAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    private Chat chat;

    @Column(name = "type", length = 32, nullable = false)
    private String type = "image";

    @Column(name = "url", columnDefinition = "TEXT", nullable = false)
    private String url;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "prompt", columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "provider", length = 50)
    private String provider;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    private String metadata = "{}";

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
    }
}
