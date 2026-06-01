package com.sandbox.sandman.backend.adapters;

import java.util.Map;

public record ExternalJobRef(
        String sourceJobKey,
        String url,
        String title,
        String company,
        String locationText,
        Map<String, Object> metadata
) {
    public ExternalJobRef(String sourceJobKey, String url, String title, String company, String locationText) {
        this(sourceJobKey, url, title, company, locationText, Map.of());
    }
}
