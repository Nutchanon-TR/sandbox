package com.sandbox.sandman.backend.model.dto;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

public record WeeklyDigestDto(
        Long id,
        Long userId,
        Long profileId,
        String status,
        LocalDate weekStart,
        LocalDate weekEnd,
        String summary,
        ZonedDateTime createdAt,
        ZonedDateTime sentAt,
        List<WeeklyDigestItemDto> items
) {
}
