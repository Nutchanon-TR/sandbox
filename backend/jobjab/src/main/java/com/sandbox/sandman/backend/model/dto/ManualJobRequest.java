package com.sandbox.sandman.backend.model.dto;

import java.util.List;

public record ManualJobRequest(
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
        String applyUrl
) {
}
