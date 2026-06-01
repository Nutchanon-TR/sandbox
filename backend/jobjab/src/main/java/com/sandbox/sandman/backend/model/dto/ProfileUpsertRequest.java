package com.sandbox.sandman.backend.model.dto;

import java.util.List;

public record ProfileUpsertRequest(
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
        Boolean weeklyDigestEnabled
) {
}
