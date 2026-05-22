package com.sandbox.sandman.backend.model.dto.PersonaFeedDto;

import java.time.ZonedDateTime;
import java.util.List;

public record PersonaFeedPostDto(
        Long id,
        PersonaFeedPersonaSummaryDto persona,
        String content,
        List<String> imageUrls,
        ZonedDateTime createdAt,
        boolean followedByMe
) {
}
