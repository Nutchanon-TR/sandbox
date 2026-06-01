package com.sandbox.sandman.backend.model.dto;

import java.time.ZonedDateTime;
import java.util.List;

public record SearchRunDto(
        Long id,
        Long userId,
        Long profileId,
        String runType,
        String status,
        String query,
        String locationText,
        Integer requestedLimit,
        List<String> sourceKeys,
        ZonedDateTime startedAt,
        ZonedDateTime completedAt,
        ZonedDateTime createdAt
) {
}
