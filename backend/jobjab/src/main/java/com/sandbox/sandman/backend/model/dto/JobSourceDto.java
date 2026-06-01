package com.sandbox.sandman.backend.model.dto;

public record JobSourceDto(
        Long id,
        String sourceKey,
        String displayName,
        String fetchMode,
        boolean enabled,
        boolean registered,
        boolean configured,
        boolean searchable,
        boolean acceptsPerSearchTargets,
        boolean requiresJobUrl,
        boolean requiresPartnerApproval,
        boolean robotsCheckRequired,
        Integer rateLimitPerMinute,
        String targetHint,
        String targetExample,
        String guidance
) {
}
