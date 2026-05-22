package com.sandbox.sandman.backend.model.dto.PersonaFeedDto;

import java.util.List;

public record CursorPageDto<T>(
        List<T> items,
        boolean hasMore,
        Long nextCursor
) {
}
