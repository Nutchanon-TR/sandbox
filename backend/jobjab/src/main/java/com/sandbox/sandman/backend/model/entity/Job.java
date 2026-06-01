package com.sandbox.sandman.backend.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "jobs", schema = "jobjab")
@Data
@NoArgsConstructor
public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sourceId;

    @Column(nullable = false)
    private String sourceJobKey;

    private Long latestSnapshotId;
    private String canonicalUrl;

    @Column(nullable = false, length = 240)
    private String title;

    @Column(nullable = false, length = 200)
    private String company = "Unknown company";

    @Column(columnDefinition = "TEXT")
    private String locationText;

    private Double locationLatitude;
    private Double locationLongitude;
    private Integer salaryMin;
    private Integer salaryMax;
    private String currency = "THB";
    private String employmentType;
    private String workplaceType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> skills = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String description = "";

    private String applyUrl;
    private ZonedDateTime postedAt;
    private ZonedDateTime firstSeenAt;
    private ZonedDateTime lastSeenAt;
    private ZonedDateTime archivedAt;

    @PrePersist
    void onCreate() {
        ZonedDateTime now = ZonedDateTime.now();
        if (firstSeenAt == null) firstSeenAt = now;
        if (lastSeenAt == null) lastSeenAt = now;
    }

    @PreUpdate
    void onUpdate() {
        lastSeenAt = ZonedDateTime.now();
    }
}
