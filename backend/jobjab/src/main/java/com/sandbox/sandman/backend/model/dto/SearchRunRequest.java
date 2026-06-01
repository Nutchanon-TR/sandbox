package com.sandbox.sandman.backend.model.dto;

import java.util.List;
import java.util.Map;

public record SearchRunRequest(
        String query,
        String locationText,
        Integer limit,
        String jobUrl,
        List<String> jobUrls,
        List<ManualJobRequest> manualJobs,
        Map<String, List<String>> sourceTargets,
        List<String> sourceKeys
) {
}
