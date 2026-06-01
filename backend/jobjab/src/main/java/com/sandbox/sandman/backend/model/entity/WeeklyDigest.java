package com.sandbox.sandman.backend.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.ZonedDateTime;

@Entity
@Table(name = "weekly_digests", schema = "jobjab")
@Data
@NoArgsConstructor
public class WeeklyDigest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private Long profileId;
    private String status = "DRAFT";
    private LocalDate weekStart;
    private LocalDate weekEnd;
    private String summary;
    private ZonedDateTime createdAt;
    private ZonedDateTime sentAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = ZonedDateTime.now();
    }
}
