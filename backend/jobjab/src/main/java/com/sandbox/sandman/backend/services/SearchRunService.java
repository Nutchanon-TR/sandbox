package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.model.dto.SearchRunDto;
import com.sandbox.sandman.backend.model.dto.SearchRunEventDto;
import com.sandbox.sandman.backend.model.dto.SearchRunRequest;
import com.sandbox.sandman.backend.model.entity.*;
import com.sandbox.sandman.backend.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SearchRunService {
    private final SearchRunRepository searchRunRepository;
    private final SearchRunEventRepository eventRepository;
    private final UserJobProfileRepository profileRepository;
    private final JobjabMapper mapper;
    private final SearchRunWorkerService searchRunWorkerService;

    public SearchRunDto start(Long userId, SearchRunRequest req) {
        if (req == null) req = new SearchRunRequest(null, null, 10, null, List.of(), List.of(), Map.of(), List.of());
        UserJobProfile profile = profileRepository.findByUserId(userId).orElse(null);
        List<String> requestedSources = requestedSources(req.sourceKeys());
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
        event(run.getId(), "INFO", null, "JOBJAB search queued", Map.of(
                "limit", run.getRequestedLimit(),
                "query", effectiveReq.query(),
                "locationText", effectiveReq.locationText(),
                "profileDefaultsApplied", profileDefaultsApplied(effectiveReq, req)
        ));
        searchRunWorkerService.execute(run.getId(), effectiveReq);
        return mapper.toDto(run);
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

    private List<String> requestedSources(List<String> sourceKeys) {
        if (sourceKeys == null || sourceKeys.isEmpty()) {
            return List.of("manual_jd", "user_url", "greenhouse", "lever", "ashby", "structured_data", "sitemap");
        }
        return sourceKeys.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).distinct().toList();
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

}
