package com.sandbox.sandman.backend.model.dto;

import java.time.ZonedDateTime;
import java.util.List;

public record ProfileDto(
        Long id,
        Long userId,
        String headline,
        List<String> desiredTitles,
        List<String> skills,
        List<String> experiences,
        List<String> preferredLocations,
        List<String> employmentTypes,
        Integer salaryMin,
        Integer salaryMax,
        String homeLocationLabel,
        Double homeLatitude,
        Double homeLongitude,
        Integer maxCommuteMinutes,
        String travelMode,
        boolean weeklyDigestEnabled,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) {
}
