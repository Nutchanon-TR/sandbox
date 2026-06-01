package com.sandbox.sandman.backend.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "job_tracking", schema = "jobjab")
@Data
@NoArgsConstructor
public class JobTracking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long jobId;

    @Column(nullable = false, length = 32)
    private String status = "INTERESTED";

    @Column(columnDefinition = "TEXT")
    private String notes;

    private ZonedDateTime appliedAt;
    private ZonedDateTime interviewAt;
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
