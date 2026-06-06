package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.model.dto.SearchRunDto;
import com.sandbox.sandman.backend.model.dto.SearchRunEventDto;
import com.sandbox.sandman.backend.model.dto.SearchRunRequest;
import com.sandbox.sandman.backend.model.entity.*;
import com.sandbox.sandman.backend.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SearchRunService {
    private static final List<String> ACTIVE_STATUSES = List.of("QUEUED", "RUNNING");

    @Value("${app.jobjab.search.active-run-ttl-minutes:30}")
    private long activeRunTtlMinutes = 30;

    private final SearchRunRepository searchRunRepository;
    private final SearchRunEventRepository eventRepository;
    private final UserJobProfileRepository profileRepository;
    private final JobjabMapper mapper;
    private final SearchRunWorkerService searchRunWorkerService;

    public SearchRunDto start(Long userId, SearchRunRequest req) {
        if (req == null) req = new SearchRunRequest(null, null, 10, null, List.of(), List.of(), Map.of(), List.of());
        Optional<SearchRun> activeRun = searchRunRepository.findFirstByUserIdAndStatusInOrderByIdDesc(userId, ACTIVE_STATUSES);
        if (activeRun.isPresent()) {
            SearchRun run = activeRun.get();
            if (isStaleActiveRun(run)) {
                markStaleActiveRunFailed(run);
            } else {
                event(run.getId(), "INFO", null, "Existing active JOBJAB search reused", Map.of("status", run.getStatus()));
                return mapper.toDto(run);
            }
        }
        UserJobProfile profile = profileRepository.findByUserId(userId).orElse(null);
        List<String> requestedSources = requestedSources(req);
        int requestedLimit = req.limit() == null ? 10 : Math.min(Math.max(req.limit(), 1), 50);
        SearchRunRequest effectiveReq = effectiveRequest(req, profile, requestedSources, requestedLimit);
        SearchRun run = new SearchRun();
        run.setUserId(userId);
        run.setProfileId(profile == null ? null : profile.getId());
        run.setStatus("QUEUED");
        run.setQuery(effectiveReq.query());
        run.setLocationText(effectiveReq.locationText());
        run.setRequestedLimit(requestedLimit);
        run.setSourceKeys(requestedSources);
        run.setCriteria(criteria(effectiveReq, req));
        run = searchRunRepository.save(run);
        event(run.getId(), "INFO", null, "JOBJAB search queued", queuedPayload(run, effectiveReq, req));
        searchRunWorkerService.execute(run.getId(), effectiveReq);
        return mapper.toDto(run);
    }

    private boolean isStaleActiveRun(SearchRun run) {
        if (activeRunTtlMinutes <= 0) return false;
        ZonedDateTime anchor = run.getStartedAt() != null ? run.getStartedAt() : run.getCreatedAt();
        return anchor != null && anchor.isBefore(ZonedDateTime.now().minusMinutes(activeRunTtlMinutes));
    }

    private void markStaleActiveRunFailed(SearchRun run) {
        String previousStatus = run.getStatus() == null ? "UNKNOWN" : run.getStatus();
        run.setStatus("FAILED");
        run.setCompletedAt(ZonedDateTime.now());
        searchRunRepository.save(run);
        event(run.getId(), "WARN", null, "Stale active JOBJAB search marked failed before starting a new search", Map.of(
                "previousStatus", previousStatus,
                "ttlMinutes", activeRunTtlMinutes
        ));
    }

    @Transactional(readOnly = true)
    public List<SearchRunDto> recent(Long userId) {
        return searchRunRepository.findRecentByUserId(userId, PageRequest.of(0, 20)).stream().map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public SearchRunDto get(Long userId, Long runId) {
        return mapper.toDto(searchRunRepository.findByIdAndUserId(runId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Search run not found")));
    }

    @Transactional(readOnly = true)
    public List<SearchRunEventDto> events(Long userId, Long runId) {
        searchRunRepository.findByIdAndUserId(runId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Search run not found"));
        return eventRepository.findBySearchRunIdOrderByIdAsc(runId).stream().map(mapper::toDto).toList();
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

    private Map<String, Object> queuedPayload(SearchRun run, SearchRunRequest effectiveReq, SearchRunRequest originalReq) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("limit", run.getRequestedLimit());
        if (hasText(effectiveReq.query())) payload.put("query", effectiveReq.query());
        if (hasText(effectiveReq.locationText())) payload.put("locationText", effectiveReq.locationText());
        payload.put("profileDefaultsApplied", profileDefaultsApplied(effectiveReq, originalReq));
        payload.put("sourceKeys", run.getSourceKeys());
        return payload;
    }

    private List<String> requestedSources(SearchRunRequest req) {
        if (req.sourceKeys() != null && !req.sourceKeys().isEmpty()) {
            return req.sourceKeys().stream().filter(s -> s != null && !s.isBlank()).map(String::trim).distinct().toList();
        }
        LinkedHashSet<String> defaults = new LinkedHashSet<>();
        if (hasManualJobs(req)) defaults.add("manual_jd");
        if (hasJobUrls(req)) {
            defaults.add("user_url");
            defaults.add("structured_data");
            defaults.addAll(inferredSourceKeys(req));
        }
        defaults.addAll(sourceTargets(req));
        if (!defaults.isEmpty()) return defaults.stream().toList();
        return List.of("manual_jd", "user_url", "greenhouse", "lever", "ashby", "structured_data", "sitemap");
    }

    private SearchRunRequest effectiveRequest(SearchRunRequest req, UserJobProfile profile, List<String> requestedSources, int requestedLimit) {
        String query = hasText(req.query()) ? req.query().trim() : firstText(profile == null ? null : profile.getDesiredTitles());
        String locationText = hasText(req.locationText()) ? req.locationText().trim() : firstText(profile == null ? null : profile.getPreferredLocations());
        return new SearchRunRequest(
                query,
                locationText,
                requestedLimit,
                trimToNull(req.jobUrl()),
                cleaned(req.jobUrls()),
                req.manualJobs() == null ? List.of() : req.manualJobs(),
                req.sourceTargets() == null ? Map.of() : req.sourceTargets(),
                requestedSources
        );
    }

    private Map<String, Boolean> profileDefaultsApplied(SearchRunRequest effectiveReq, SearchRunRequest originalReq) {
        return Map.of(
                "query", !hasText(originalReq.query()) && hasText(effectiveReq.query()),
                "locationText", !hasText(originalReq.locationText()) && hasText(effectiveReq.locationText())
        );
    }

    private Map<String, Object> criteria(SearchRunRequest req, SearchRunRequest originalReq) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("query", req.query());
        map.put("locationText", req.locationText());
        map.put("limit", req.limit());
        map.put("jobUrl", req.jobUrl());
        map.put("jobUrls", req.jobUrls());
        map.put("manualJobs", req.manualJobs());
        map.put("sourceTargets", req.sourceTargets());
        map.put("sourceKeys", req.sourceKeys());
        map.put("profileDefaultsApplied", profileDefaultsApplied(req, originalReq));
        return map;
    }

    private String firstText(List<String> values) {
        if (values == null) return null;
        return values.stream()
                .filter(this::hasText)
                .map(String::trim)
                .findFirst()
                .orElse(null);
    }

    private List<String> cleaned(List<String> values) {
        if (values == null) return List.of();
        return values.stream()
                .filter(this::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean hasManualJobs(SearchRunRequest req) {
        return req.manualJobs() != null && req.manualJobs().stream()
                .filter(Objects::nonNull)
                .anyMatch(job -> hasText(job.title()) || hasText(job.description()) || hasText(job.applyUrl()));
    }

    private boolean hasJobUrls(SearchRunRequest req) {
        return hasText(req.jobUrl()) || (req.jobUrls() != null && req.jobUrls().stream().anyMatch(this::hasText));
    }

    private List<String> sourceTargets(SearchRunRequest req) {
        if (req.sourceTargets() == null) return List.of();
        return req.sourceTargets().entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                .filter(entry -> entry.getValue() != null && entry.getValue().stream().anyMatch(this::hasText))
                .map(entry -> entry.getKey().trim())
                .distinct()
                .toList();
    }

    private List<String> inferredSourceKeys(SearchRunRequest req) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        List<String> urls = new ArrayList<>();
        if (hasText(req.jobUrl())) urls.add(req.jobUrl());
        if (req.jobUrls() != null) urls.addAll(cleaned(req.jobUrls()));
        for (String url : urls) {
            try {
                String host = java.net.URI.create(url).getHost();
                if (host == null) continue;
                host = host.toLowerCase(Locale.ROOT);
                if (host.equals("boards.greenhouse.io") || host.equals("job-boards.greenhouse.io") || host.equals("boards-api.greenhouse.io")) {
                    keys.add("greenhouse");
                }
                if (host.equals("jobs.lever.co") || host.equals("api.lever.co")) {
                    keys.add("lever");
                }
                if (host.equals("jobs.ashbyhq.com") || host.equals("api.ashbyhq.com")) {
                    keys.add("ashby");
                }
            } catch (Exception ignored) {
                // Invalid URLs are left for adapters to report during extraction.
            }
        }
        return keys.stream().toList();
    }

}
