package com.sandbox.sandman.backend.model.dto;

import java.time.ZonedDateTime;
import java.util.List;

public record JobDto(
        Long id,
        Long sourceId,
        String sourceJobKey,
        String canonicalUrl,
        String title,
        String company,
        String locationText,
        Double locationLatitude,
        Double locationLongitude,
        Integer salaryMin,
        Integer salaryMax,
        String currency,
        String employmentType,
        String workplaceType,
        List<String> skills,
        String description,
        String applyUrl,
        ZonedDateTime postedAt,
        ZonedDateTime firstSeenAt,
        ZonedDateTime lastSeenAt,
        JobMatchDto match,
        JobTrackingDto tracking,
        RouteDto route
) {
}
