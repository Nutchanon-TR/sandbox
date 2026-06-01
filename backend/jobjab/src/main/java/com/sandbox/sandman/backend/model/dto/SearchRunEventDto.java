package com.sandbox.sandman.backend.model.dto;

import java.time.ZonedDateTime;
import java.util.Map;

public record SearchRunEventDto(
        Long id,
        Long searchRunId,
        String level,
        String sourceKey,
        String message,
        Map<String, Object> payload,
        ZonedDateTime createdAt
) {
}
