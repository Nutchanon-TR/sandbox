package com.sandbox.sandman.backend.model.dto;

import java.util.List;

public record PageResponse<T>(List<T> items, boolean hasMore, Long nextCursor) {
}
