package com.sandbox.sandman.backend.model.dto;

import java.time.ZonedDateTime;

public record JobTrackingDto(
        Long id,
        Long userId,
        Long jobId,
        String status,
        String notes,
        ZonedDateTime appliedAt,
        ZonedDateTime interviewAt,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) {
}
