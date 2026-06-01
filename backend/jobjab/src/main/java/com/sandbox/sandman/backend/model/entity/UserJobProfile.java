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
@Table(name = "user_job_profiles", schema = "jobjab")
@Data
@NoArgsConstructor
public class UserJobProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(length = 160)
    private String headline;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "desired_titles", columnDefinition = "jsonb")
    private List<String> desiredTitles = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> skills = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> experiences = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_locations", columnDefinition = "jsonb")
    private List<String> preferredLocations = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "employment_types", columnDefinition = "jsonb")
    private List<String> employmentTypes = new ArrayList<>();

    private Integer salaryMin;
    private Integer salaryMax;
    private String homeLocationLabel;
    private Double homeLatitude;
    private Double homeLongitude;
    private Integer maxCommuteMinutes;
    private String travelMode = "DRIVE";
    private boolean weeklyDigestEnabled = true;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    @PrePersist
    void onCreate() {
        ZonedDateTime now = ZonedDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = ZonedDateTime.now();
    }
}
