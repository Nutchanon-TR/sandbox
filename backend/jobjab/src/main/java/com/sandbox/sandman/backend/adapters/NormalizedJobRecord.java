package com.sandbox.sandman.backend.adapters;

import java.time.ZonedDateTime;
import java.util.List;

public record NormalizedJobRecord(
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
        ZonedDateTime postedAt
) {
}
