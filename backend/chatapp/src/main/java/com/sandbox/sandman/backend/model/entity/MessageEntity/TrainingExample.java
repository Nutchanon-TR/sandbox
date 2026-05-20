package com.sandbox.sandman.backend.model.entity.MessageEntity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;

import java.time.ZonedDateTime;

@Entity
@Table(name = "training_examples", schema = "chat_app")
@Data
@NoArgsConstructor
public class TrainingExample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_context_id", nullable = false)
    private AiContext aiContext;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_chat_id")
    private Chat sourceChat;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "messages_json", columnDefinition = "jsonb", nullable = false)
    private String messagesJson = "[]";

    @Column(name = "status", length = 32, nullable = false)
    private String status = "draft";

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "tags", columnDefinition = "jsonb", nullable = false)
    private String tags = "[]";

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
    }
}
