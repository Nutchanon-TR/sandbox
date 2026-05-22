package com.sandbox.sandman.backend.model.dto.PersonaFeedDto;

public record PersonaFeedPersonaProfileDto(
        Long id,
        String aiName,
        String avatarUrl,
        String posterUrl,
        String role,
        String biography,
        boolean followedByMe
) {
}
