package com.sandbox.sandman.backend.model.dto;

import java.time.ZonedDateTime;

public record RouteDto(
        Long id,
        Long userId,
        Long jobId,
        String travelMode,
        String originLabel,
        String destinationLabel,
        Integer distanceMeters,
        Integer durationSeconds,
        String provider,
        ZonedDateTime createdAt,
        ZonedDateTime expiresAt
) {
}
