package com.sandbox.sandman.backend.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sandbox.sandman.backend.adapters.*;
import com.sandbox.sandman.backend.model.dto.SearchRunRequest;
import com.sandbox.sandman.backend.model.entity.*;
import com.sandbox.sandman.backend.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchRunWorkerService {
    private final List<JobSourceAdapter> adapters;
    private final SearchRunRepository searchRunRepository;
    private final SearchRunEventRepository eventRepository;
    private final RawJobSnapshotRepository rawJobSnapshotRepository;
    private final JobSourceService jobSourceService;
    private final JobService jobService;
    private final MatchAnalysisService matchAnalysisService;
    private final ObjectMapper objectMapper;

    @Value("${app.jobjab.ai.auto-analyze-limit:10}")
    private int autoAnalyzeLimit;

    @Async
    public void execute(Long runId, SearchRunRequest req) {
        SearchRun run = searchRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Search run not found"));
        run.setStatus("RUNNING");
        run.setStartedAt(ZonedDateTime.now());
        searchRunRepository.save(run);
        event(run.getId(), "INFO", null, "JOBJAB search started", Map.of("limit", run.getRequestedLimit()));

        int savedCount = 0;
        int analyzedCount = 0;
        int sourceErrors = 0;
        Map<String, JobSourceAdapter> byKey = adapters.stream().collect(Collectors.toMap(JobSourceAdapter::sourceKey, Function.identity()));

        for (String sourceKey : run.getSourceKeys()) {
            int remaining = run.getRequestedLimit() - savedCount;
            if (remaining <= 0) {
                event(run.getId(), "INFO", null, "Requested result count reached", Map.of("savedJobs", savedCount));
                break;
            }
            JobSourceAdapter adapter = byKey.get(sourceKey);
            if (adapter == null) {
                event(run.getId(), "WARN", sourceKey, "Adapter is not registered", Map.of());
                continue;
            }
            try {
                JobSource source = jobSourceService.ensureSource(adapter);
                Map<String, Object> sourceConfig = sourceConfig(sourceKey, source.getAdapterConfig(), req.sourceTargets());
                event(run.getId(), "INFO", sourceKey, "Searching source", Map.of("fetchMode", adapter.fetchMode().name()));
                if (requiresConfiguredTargets(adapter) && !hasConfiguredTargets(sourceConfig)) {
                    event(run.getId(), "WARN", sourceKey, "Source needs adapter_config or per-search targets before it can return jobs", Map.of(
                            "example", Map.of("sourceTargets", Map.of(sourceKey, List.of(exampleTarget(sourceKey))))
                    ));
                }

                JobSearchCriteria criteria = new JobSearchCriteria(req.query(), req.locationText(), remaining, req.jobUrl(), urls(req), manualJobs(req), run.getSourceKeys(), sourceConfig);
                List<ExternalJobRef> refs = adapter.search(criteria).stream().limit(remaining).toList();
                if (refs.isEmpty()) {
                    event(run.getId(), "INFO", sourceKey, "No jobs returned for this source", Map.of());
                    continue;
                }
                int jobErrors = 0;
                for (ExternalJobRef ref : refs) {
                    try {
                        RawJobPayload raw = adapter.fetchDetail(ref);
                        String hash = hash(raw);
                        RawJobSnapshot snapshot = rawJobSnapshotRepository
                                .findBySourceIdAndSourceJobKeyAndContentHash(source.getId(), raw.sourceJobKey(), hash)
                                .orElseGet(() -> saveSnapshot(source, raw, hash));
                        Job job = jobService.upsert(source.getId(), snapshot.getId(), adapter.normalize(raw));
                        boolean analyzed = shouldAutoAnalyze(analyzedCount);
                        if (analyzed) {
                            matchAnalysisService.analyze(run.getUserId(), job.getId());
                            analyzedCount++;
                        }
                        savedCount++;
                        event(run.getId(), "INFO", sourceKey, "Saved job: " + job.getTitle(), Map.of(
                                "jobId", job.getId(),
                                "matchAnalysis", analyzed ? "AUTO" : "SKIPPED_AUTO_ANALYZE_LIMIT"
                        ));
                    } catch (RuntimeException ex) {
                        jobErrors++;
                        event(run.getId(), "WARN", sourceKey, "Skipped job after extract error", errorPayload(ref, ex));
                    }
                }
                if (jobErrors > 0) sourceErrors++;
                event(run.getId(), "INFO", sourceKey, "Source completed", Map.of(
                        "jobs", refs.size(),
                        "jobErrors", jobErrors,
                        "savedJobs", savedCount,
                        "requestedLimit", run.getRequestedLimit()
                ));
            } catch (RuntimeException ex) {
                sourceErrors++;
                event(run.getId(), "ERROR", sourceKey, "Source failed; continuing with remaining sources", Map.of("error", errorMessage(ex)));
            }
        }
        run.setStatus(sourceErrors == 0 ? "COMPLETED" : savedCount > 0 ? "PARTIAL" : "FAILED");
        event(run.getId(), "INFO", null, statusMessage(run.getStatus()), Map.of(
                "savedJobs", savedCount,
                "analyzedJobs", analyzedCount,
                "sourceErrors", sourceErrors,
                "autoAnalyzeLimit", normalizedAutoAnalyzeLimit()
        ));
        run.setCompletedAt(ZonedDateTime.now());
        searchRunRepository.save(run);
    }

    private String statusMessage(String status) {
        return switch (status) {
            case "PARTIAL" -> "JOBJAB search completed with source warnings";
            case "FAILED" -> "JOBJAB search failed across all selected sources";
            default -> "JOBJAB search completed";
        };
    }

    private Map<String, Object> errorPayload(ExternalJobRef ref, RuntimeException ex) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (ref.sourceJobKey() != null) payload.put("sourceJobKey", ref.sourceJobKey());
        if (ref.url() != null) payload.put("url", ref.url());
        payload.put("error", errorMessage(ex));
        return payload;
    }

    private String errorMessage(RuntimeException ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private boolean shouldAutoAnalyze(int analyzedCount) {
        return analyzedCount < normalizedAutoAnalyzeLimit();
    }

    private int normalizedAutoAnalyzeLimit() {
        return Math.max(0, autoAnalyzeLimit);
    }

    private RawJobSnapshot saveSnapshot(JobSource source, RawJobPayload raw, String hash) {
        RawJobSnapshot snapshot = new RawJobSnapshot();
        snapshot.setSourceId(source.getId());
        snapshot.setSourceJobKey(raw.sourceJobKey());
        snapshot.setUrl(raw.url());
        snapshot.setRawPayload(raw.payload() == null ? Map.of() : raw.payload());
        snapshot.setRawText(raw.rawText());
        snapshot.setContentHash(hash);
        return rawJobSnapshotRepository.save(snapshot);
    }

    private void event(Long runId, String level, String sourceKey, String message, Map<String, Object> payload) {
        SearchRunEvent event = new SearchRunEvent();
        event.setSearchRunId(runId);
        event.setLevel(level);
        event.setSourceKey(sourceKey);
        event.setMessage(message);
        event.setPayload(payload == null ? Map.of() : payload);
        eventRepository.save(event);
    }

    private boolean requiresConfiguredTargets(JobSourceAdapter adapter) {
        return adapter.fetchMode() == JobFetchMode.OFFICIAL_API || adapter.fetchMode() == JobFetchMode.SITEMAP;
    }

    private boolean hasConfiguredTargets(Map<String, Object> sourceConfig) {
        if (sourceConfig == null || sourceConfig.isEmpty()) return false;
        return List.of("boards", "boardTokens", "companies", "sites", "jobBoardNames", "sitemapUrls", "urls", "targets").stream()
                .map(sourceConfig::get)
                .anyMatch(value -> value instanceof List<?> list && !list.isEmpty()
                        || value instanceof String text && !text.isBlank());
    }

    private Map<String, Object> sourceConfig(String sourceKey, Map<String, Object> baseConfig, Map<String, List<String>> sourceTargets) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (baseConfig != null) merged.putAll(baseConfig);
        List<String> targets = sourceTargets == null ? List.of() : cleaned(sourceTargets.get(sourceKey));
        if (targets.isEmpty()) return merged;
        switch (sourceKey) {
            case "greenhouse", "ashby" -> mergeList(merged, "boards", targets);
            case "lever" -> mergeList(merged, "companies", targets);
            case "sitemap" -> mergeList(merged, "sitemapUrls", targets);
            default -> mergeList(merged, "targets", targets);
        }
        merged.put("perSearchTargets", targets);
        return merged;
    }

    private String exampleTarget(String sourceKey) {
        return "sitemap".equals(sourceKey) ? "https://example.com/sitemap.xml" : "company-slug";
    }

    private List<String> urls(SearchRunRequest req) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        if (req.jobUrl() != null && !req.jobUrl().isBlank()) urls.add(req.jobUrl().trim());
        if (req.jobUrls() != null) urls.addAll(cleaned(req.jobUrls()));
        return urls.stream().toList();
    }

    private List<com.sandbox.sandman.backend.model.dto.ManualJobRequest> manualJobs(SearchRunRequest req) {
        if (req.manualJobs() == null) return List.of();
        return req.manualJobs().stream()
                .filter(Objects::nonNull)
                .filter(job -> hasText(job.title()) || hasText(job.description()) || hasText(job.applyUrl()))
                .toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void mergeList(Map<String, Object> config, String key, List<String> additions) {
        LinkedHashSet<String> values = new LinkedHashSet<>(cleaned(config.get(key)));
        values.addAll(additions);
        config.put(key, values.stream().toList());
    }

    private List<String> cleaned(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item != null && !String.valueOf(item).isBlank())
                    .map(item -> String.valueOf(item).trim())
                    .distinct()
                    .toList();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Arrays.stream(text.split(","))
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .distinct()
                    .toList();
        }
        return List.of();
    }

    private String hash(RawJobPayload raw) {
        try {
            String text = objectMapper.writeValueAsString(raw.payload()) + "|" + raw.rawText();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to serialize raw job payload", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash raw job payload", ex);
        }
    }
}
