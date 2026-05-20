package com.sandbox.sandman.backend.model.entity.MessageEntity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;

import java.time.ZonedDateTime;

@Entity
@Table(name = "training_runs", schema = "chat_app")
@Data
@NoArgsConstructor
public class TrainingRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_context_id", nullable = false)
    private AiContext aiContext;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    @Column(name = "provider", length = 50, nullable = false)
    private String provider = "manual";

    @Column(name = "base_model", columnDefinition = "TEXT")
    private String baseModel;

    @Column(name = "dataset_version", columnDefinition = "TEXT")
    private String datasetVersion;

    @Column(name = "status", length = 32, nullable = false)
    private String status = "planned";

    @Column(name = "external_job_id", columnDefinition = "TEXT")
    private String externalJobId;

    @Column(name = "fine_tuned_model_id", columnDefinition = "TEXT")
    private String fineTunedModelId;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "metrics", columnDefinition = "jsonb", nullable = false)
    private String metrics = "{}";

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        ZonedDateTime now = ZonedDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = ZonedDateTime.now();
    }
}
