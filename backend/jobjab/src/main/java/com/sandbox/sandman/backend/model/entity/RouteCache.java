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
@Table(name = "route_cache", schema = "jobjab")
@Data
@NoArgsConstructor
public class RouteCache {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long jobId;

    @Column(nullable = false, unique = true, length = 128)
    private String cacheKey;

    @Column(nullable = false, length = 32)
    private String travelMode = "DRIVE";

    private String originLabel;
    private String destinationLabel;
    private Integer distanceMeters;
    private Integer durationSeconds;
    private String provider = "ESTIMATE";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> rawPayload = new LinkedHashMap<>();

    private ZonedDateTime createdAt;
    private ZonedDateTime expiresAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = ZonedDateTime.now();
    }
}
