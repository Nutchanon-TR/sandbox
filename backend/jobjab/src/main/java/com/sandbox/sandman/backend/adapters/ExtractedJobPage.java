package com.sandbox.sandman.backend.adapters;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

public record ExtractedJobPage(
        String title,
        String company,
        String locationText,
        String description,
        Integer salaryMin,
        Integer salaryMax,
        String currency,
        String employmentType,
        String workplaceType,
        List<String> skills,
        ZonedDateTime postedAt,
        Map<String, Object> payload
) {
}
