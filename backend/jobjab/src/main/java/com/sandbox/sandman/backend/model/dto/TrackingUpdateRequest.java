package com.sandbox.sandman.backend.model.dto;

public record TrackingUpdateRequest(
        String status,
        String notes
) {
}
