package com.sandbox.sandman.backend.model.dto;

import java.time.ZonedDateTime;
import java.util.List;

public record JobMatchDto(
        Long id,
        Long userId,
        Long jobId,
        Long profileId,
        Integer matchScore,
        List<String> matchedSkills,
        List<String> missingSkills,
        List<String> redFlags,
        String aiSummary,
        ZonedDateTime analyzedAt
) {
}
