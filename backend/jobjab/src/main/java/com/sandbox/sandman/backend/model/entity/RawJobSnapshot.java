package com.sandbox.sandman.backend.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "raw_job_snapshots", schema = "jobjab")
@Data
@NoArgsConstructor
public class RawJobSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sourceId;

    @Column(nullable = false)
    private String sourceJobKey;

    private String url;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> rawPayload = new LinkedHashMap<>();

    @Column(columnDefinition = "TEXT")
    private String rawText;

    @Column(nullable = false, length = 128)
    private String contentHash;

    private ZonedDateTime fetchedAt;

    @PrePersist
    void onCreate() {
        if (fetchedAt == null) fetchedAt = ZonedDateTime.now();
    }
}
