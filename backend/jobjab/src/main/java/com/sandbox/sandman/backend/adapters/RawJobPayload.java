package com.sandbox.sandman.backend.adapters;

import java.util.Map;

public record RawJobPayload(
        String sourceJobKey,
        String url,
        String rawText,
        Map<String, Object> payload
) {
}
