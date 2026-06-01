package com.sandbox.sandman.backend.model.dto;

public record WeeklyDigestItemDto(
        Long id,
        Long weeklyDigestId,
        Long jobId,
        Long matchId,
        Integer rank,
        String reason,
        JobDto job
) {
}
