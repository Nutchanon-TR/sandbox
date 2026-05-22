package com.sandbox.sandman.backend.model.dto.PersonaFeedDto;

public record PersonaFeedPersonaSummaryDto(
        Long id,
        String aiName,
        String avatarUrl,
        String posterUrl
) {
}
