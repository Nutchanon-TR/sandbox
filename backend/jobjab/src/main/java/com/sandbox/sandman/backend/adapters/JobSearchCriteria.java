package com.sandbox.sandman.backend.adapters;

import com.sandbox.sandman.backend.model.dto.ManualJobRequest;

import java.util.List;
import java.util.Map;

public record JobSearchCriteria(
        String query,
        String locationText,
        Integer limit,
        String jobUrl,
        List<String> jobUrls,
        List<ManualJobRequest> manualJobs,
        List<String> sourceKeys,
        Map<String, Object> sourceConfig
) {
}
