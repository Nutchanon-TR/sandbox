package com.sandbox.sandman.backend.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "weekly_digest_items", schema = "jobjab")
@Data
@NoArgsConstructor
public class WeeklyDigestItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long weeklyDigestId;
    private Long jobId;
    private Long matchId;
    private Integer rank;
    private String reason;
}
