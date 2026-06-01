package com.sandbox.sandman.backend.adapters;

import com.sandbox.sandman.backend.model.dto.ManualJobRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Component
public class ManualJobAdapter implements JobSourceAdapter {
    @Override
    public String sourceKey() {
        return "manual_jd";
    }

    @Override
    public JobFetchMode fetchMode() {
        return JobFetchMode.MANUAL;
    }

    @Override
    public List<ExternalJobRef> search(JobSearchCriteria criteria) {
        if (criteria == null || criteria.manualJobs() == null || criteria.manualJobs().isEmpty()) {
            return List.of();
        }
        int limit = criteria.limit() == null ? 10 : Math.max(1, criteria.limit());
        return criteria.manualJobs().stream()
                .filter(this::hasEnoughDetail)
                .limit(limit)
                .map(job -> new ExternalJobRef(
                        sourceJobKey(job),
                        blankToNull(job.applyUrl()),
                        fallback(job.title(), "Manual job"),
                        fallback(job.company(), "Unknown company"),
                        fallback(job.locationText(), criteria.locationText()),
                        payload(job)
                ))
                .toList();
    }

    @Override
    public RawJobPayload fetchDetail(ExternalJobRef ref) {
        Map<String, Object> payload = new LinkedHashMap<>(ref.metadata() == null ? Map.of() : ref.metadata());
        payload.put("importMode", "MANUAL_JD");
        return new RawJobPayload(ref.sourceJobKey(), ref.url(), text(payload.get("description")), payload);
    }

    @Override
    public NormalizedJobRecord normalize(RawJobPayload raw) {
        Map<String, Object> payload = raw.payload() == null ? Map.of() : raw.payload();
        String title = fallback(text(payload.get("title")), "Manual job");
        String description = fallback(text(payload.get("description")), "");
        List<String> skills = mergedSkills(payload.get("skills"), title + " " + description);
        return new NormalizedJobRecord(
                raw.sourceJobKey(),
                text(payload.get("applyUrl")),
                title,
                fallback(text(payload.get("company")), "Unknown company"),
                text(payload.get("locationText")),
                doubleValue(payload.get("locationLatitude")),
                doubleValue(payload.get("locationLongitude")),
                intValue(payload.get("salaryMin")),
                intValue(payload.get("salaryMax")),
                fallback(text(payload.get("currency")), "THB"),
                text(payload.get("employmentType")),
                text(payload.get("workplaceType")),
                skills,
                description,
                text(payload.get("applyUrl")),
                null
        );
    }

    private boolean hasEnoughDetail(ManualJobRequest job) {
        if (job == null) return false;
        return notBlank(job.title()) || notBlank(job.description()) || notBlank(job.applyUrl());
    }

    private Map<String, Object> payload(ManualJobRequest job) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", blankToNull(job.title()));
        payload.put("company", blankToNull(job.company()));
        payload.put("locationText", blankToNull(job.locationText()));
        payload.put("locationLatitude", job.locationLatitude());
        payload.put("locationLongitude", job.locationLongitude());
        payload.put("salaryMin", job.salaryMin());
        payload.put("salaryMax", job.salaryMax());
        payload.put("currency", fallback(job.currency(), "THB"));
        payload.put("employmentType", blankToNull(job.employmentType()));
        payload.put("workplaceType", blankToNull(job.workplaceType()));
        payload.put("skills", cleanList(job.skills()));
        payload.put("description", blankToNull(job.description()));
        payload.put("applyUrl", blankToNull(job.applyUrl()));
        return payload;
    }

    private String sourceJobKey(ManualJobRequest job) {
        String stable = fallback(job.applyUrl(), "") + "|" + fallback(job.title(), "") + "|" + fallback(job.company(), "")
                + "|" + fallback(job.locationText(), "") + "|" + fallback(job.description(), "");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(stable.getBytes(StandardCharsets.UTF_8));
            return "manual:" + HexFormat.of().formatHex(digest).substring(0, 32);
        } catch (Exception ex) {
            return "manual:" + Math.abs(stable.hashCode());
        }
    }

    private List<String> mergedSkills(Object values, String text) {
        LinkedHashSet<String> skills = new LinkedHashSet<>(cleanList(values));
        skills.addAll(AdapterSupport.inferSkills(text));
        return skills.stream().toList();
    }

    private List<String> cleanList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item != null && !String.valueOf(item).isBlank())
                    .map(item -> String.valueOf(item).trim())
                    .distinct()
                    .toList();
        }
        return List.of();
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer intValue(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value == null) return null;
        try {
            return (int) Math.round(Double.parseDouble(String.valueOf(value)));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Double doubleValue(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value == null) return null;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
