package com.sandbox.sandman.backend.model.dto.PersonaFeedDto;

public record PersonaFeedFollowResponseDto(
        boolean followed,
        Long roomId
) {
}
