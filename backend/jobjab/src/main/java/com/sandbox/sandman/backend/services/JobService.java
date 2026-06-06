package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.adapters.NormalizedJobRecord;
import com.sandbox.sandman.backend.model.dto.JobDto;
import com.sandbox.sandman.backend.model.dto.PageResponse;
import com.sandbox.sandman.backend.model.entity.Job;
import com.sandbox.sandman.backend.model.entity.JobMatch;
import com.sandbox.sandman.backend.model.entity.JobTracking;
import com.sandbox.sandman.backend.model.entity.RouteCache;
import com.sandbox.sandman.backend.model.entity.UserJobProfile;
import com.sandbox.sandman.backend.repositories.JobMatchRepository;
import com.sandbox.sandman.backend.repositories.JobRepository;
import com.sandbox.sandman.backend.repositories.JobTrackingRepository;
import com.sandbox.sandman.backend.repositories.RouteCacheRepository;
import com.sandbox.sandman.backend.repositories.UserJobProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobService {
    private static final int NEARBY_CANDIDATE_LIMIT = 500;

    private final JobRepository jobRepository;
    private final JobMatchRepository jobMatchRepository;
    private final JobTrackingRepository jobTrackingRepository;
    private final RouteCacheRepository routeCacheRepository;
    private final UserJobProfileRepository profileRepository;
    private final JobjabMapper mapper;

    @Transactional
    public Job upsert(Long sourceId, Long snapshotId, NormalizedJobRecord normalized) {
        ZonedDateTime now = ZonedDateTime.now();
        Job job = jobRepository.findBySourceIdAndSourceJobKey(sourceId, normalized.sourceJobKey())
                .orElseGet(Job::new);
        job.setSourceId(sourceId);
        job.setSourceJobKey(normalized.sourceJobKey());
        job.setLatestSnapshotId(snapshotId);
        job.setCanonicalUrl(normalized.canonicalUrl());
        job.setTitle(required(normalized.title(), "Untitled job"));
        job.setCompany(required(normalized.company(), "Unknown company"));
        job.setLocationText(normalized.locationText());
        job.setLocationLatitude(normalized.locationLatitude());
        job.setLocationLongitude(normalized.locationLongitude());
        job.setSalaryMin(normalized.salaryMin());
        job.setSalaryMax(normalized.salaryMax());
        job.setCurrency(required(normalized.currency(), "THB"));
        job.setEmploymentType(normalized.employmentType());
        job.setWorkplaceType(normalized.workplaceType());
        job.setSkills(normalized.skills() == null ? List.of() : normalized.skills());
        job.setDescription(normalized.description() == null ? "" : normalized.description());
        job.setApplyUrl(normalized.applyUrl());
        job.setPostedAt(normalized.postedAt());
        if (job.getFirstSeenAt() == null) {
            job.setFirstSeenAt(now);
        }
        job.setLastSeenAt(now);
        job.setArchivedAt(null);
        return jobRepository.save(job);
    }

    @Transactional(readOnly = true)
    public PageResponse<JobDto> list(Long userId, String q, int limit, boolean nearbyFirst) {
        return list(userId, q, limit, nearbyFirst, false);
    }

    @Transactional(readOnly = true)
    public PageResponse<JobDto> list(Long userId, String q, int limit, boolean nearbyFirst, boolean trackedOnly) {
        int maxLimit = trackedOnly ? 500 : 50;
        int safeLimit = Math.min(Math.max(limit, 1), maxLimit);
        int fetchLimit = safeLimit + 1;
        String query = blankToNull(q);
        UserJobProfile profile = profileRepository.findByUserId(userId).orElse(null);
        Map<Long, RouteCache> cachedRoutes = routeByJob(userId, profile);
        Map<Long, RouteCache> routes = cachedRoutes;
        List<Job> jobs = trackedOnly
                ? trackedJobs(userId, query, fetchLimit)
                : nearbyFirst && !cachedRoutes.isEmpty()
                    ? visibleJobsNearbyFirst(query, fetchLimit, cachedRoutes)
                    : jobRepository.findVisible(query, PageRequest.of(0, fetchLimit));
        if (nearbyFirst && cachedRoutes.isEmpty() && canEstimateNearby(profile)) {
            routes = estimatedRoutes(userId, profile, jobs);
        }
        if (nearbyFirst && !routes.isEmpty()) {
            jobs = jobs.stream().sorted(nearbyComparator(routes)).toList();
        }
        boolean hasMore = jobs.size() > safeLimit;
        List<Job> page = hasMore ? jobs.subList(0, safeLimit) : jobs;
        Map<Long, RouteCache> routesForDto = routes;
        List<JobDto> items = page.stream().map(job -> mapper.toDto(job, match(userId, job.getId()), tracking(userId, job.getId()), routesForDto.get(job.getId()))).toList();
        Long nextCursor = hasMore && !page.isEmpty() ? page.get(page.size() - 1).getId() : null;
        return new PageResponse<>(items, hasMore, nextCursor);
    }

    private List<Job> visibleJobsNearbyFirst(String q, int fetchLimit, Map<Long, RouteCache> routes) {
        List<Long> routedIds = routes.values().stream()
                .filter(route -> route.getJobId() != null)
                .sorted(Comparator
                        .comparing((RouteCache route) -> route.getDurationSeconds() == null ? Integer.MAX_VALUE : route.getDurationSeconds())
                        .thenComparing(RouteCache::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(RouteCache::getJobId)
                .distinct()
                .limit(NEARBY_CANDIDATE_LIMIT)
                .toList();
        List<Job> routedJobs = routedIds.isEmpty()
                ? List.of()
                : nullToList(jobRepository.findVisibleByIds(routedIds, q));
        int recentFetchLimit = Math.min(NEARBY_CANDIDATE_LIMIT, Math.max(fetchLimit, fetchLimit + routedJobs.size()));
        List<Job> recentJobs = nullToList(jobRepository.findVisible(q, PageRequest.of(0, recentFetchLimit)));
        Map<Long, Job> byId = new LinkedHashMap<>();
        routedJobs.forEach(job -> putJob(byId, job));
        recentJobs.forEach(job -> putJob(byId, job));
        return new ArrayList<>(byId.values());
    }

    @Transactional(readOnly = true)
    public JobDto get(Long userId, Long jobId) {
        Job job = find(jobId);
        UserJobProfile profile = profileRepository.findByUserId(userId).orElse(null);
        return mapper.toDto(job, match(userId, jobId), tracking(userId, jobId), routeByJob(userId, profile).get(jobId));
    }

    @Transactional(readOnly = true)
    public Job find(Long jobId) {
        return jobRepository.findById(jobId).orElseThrow(() -> new IllegalArgumentException("Job not found"));
    }

    private JobMatch match(Long userId, Long jobId) {
        return jobMatchRepository.findByUserIdAndJobId(userId, jobId).orElse(null);
    }

    private JobTracking tracking(Long userId, Long jobId) {
        return jobTrackingRepository.findByUserIdAndJobId(userId, jobId).orElse(null);
    }

    private List<Job> trackedJobs(Long userId, String q, int fetchLimit) {
        List<JobTracking> trackingRows = jobTrackingRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        if (trackingRows.isEmpty()) return List.of();
        List<Long> trackedIds = trackingRows.stream()
                .map(JobTracking::getJobId)
                .filter(id -> id != null)
                .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), set -> set.stream().limit(fetchLimit).toList()));
        if (trackedIds.isEmpty()) return List.of();
        Set<Long> allowed = new LinkedHashSet<>(trackedIds);
        Map<Long, Job> byId = jobRepository.findVisibleByIds(trackedIds, q).stream()
                .collect(Collectors.toMap(Job::getId, Function.identity()));
        return trackedIds.stream()
                .filter(allowed::contains)
                .map(byId::get)
                .filter(job -> job != null)
                .toList();
    }

    private void putJob(Map<Long, Job> byId, Job job) {
        if (job != null && job.getId() != null) byId.putIfAbsent(job.getId(), job);
    }

    private List<Job> nullToList(List<Job> jobs) {
        return jobs == null ? List.of() : jobs;
    }

    private Map<Long, RouteCache> routeByJob(Long userId, UserJobProfile profile) {
        if (profile == null || profile.getTravelMode() == null || profile.getTravelMode().isBlank()) return Map.of();
        ZonedDateTime now = ZonedDateTime.now();
        return routeCacheRepository.findByUserIdAndTravelModeOrderByCreatedAtDesc(userId, profile.getTravelMode()).stream()
                .filter(route -> route.getExpiresAt() == null || route.getExpiresAt().isAfter(now))
                .collect(Collectors.toMap(RouteCache::getJobId, Function.identity(), (latest, ignored) -> latest));
    }

    private boolean canEstimateNearby(UserJobProfile profile) {
        return profile != null
                && profile.getTravelMode() != null
                && !profile.getTravelMode().isBlank()
                && hasCoords(profile.getHomeLatitude(), profile.getHomeLongitude());
    }

    private Map<Long, RouteCache> estimatedRoutes(Long userId, UserJobProfile profile, List<Job> jobs) {
        Map<Long, RouteCache> routes = new LinkedHashMap<>();
        if (jobs == null) return routes;
        for (Job job : jobs) {
            if (job == null || job.getId() == null || !hasCoords(job.getLocationLatitude(), job.getLocationLongitude())) continue;
            RouteCache route = new RouteCache();
            route.setUserId(userId);
            route.setJobId(job.getId());
            route.setTravelMode(profile.getTravelMode());
            route.setOriginLabel(profile.getHomeLocationLabel());
            route.setDestinationLabel(job.getLocationText());
            route.setProvider("ESTIMATE");
            double km = haversineKm(profile.getHomeLatitude(), profile.getHomeLongitude(), job.getLocationLatitude(), job.getLocationLongitude());
            double roadFactor = "WALK".equals(profile.getTravelMode()) ? 1.15 : 1.35;
            int meters = (int) Math.round(km * roadFactor * 1000);
            route.setDistanceMeters(meters);
            route.setDurationSeconds((int) Math.round((meters / 1000.0) / speedKmh(profile.getTravelMode()) * 3600));
            route.setRawPayload(Map.of("method", "haversine_nearby_preview", "roadFactor", roadFactor));
            routes.put(job.getId(), route);
        }
        return routes;
    }

    private Comparator<Job> nearbyComparator(Map<Long, RouteCache> routes) {
        return Comparator
                .comparing((Job job) -> {
                    RouteCache route = routes.get(job.getId());
                    return route == null || route.getDurationSeconds() == null ? Integer.MAX_VALUE : route.getDurationSeconds();
                })
                .thenComparing(Job::getLastSeenAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Job::getId, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private String required(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean hasCoords(Double lat, Double lng) {
        return lat != null && lng != null;
    }

    private int speedKmh(String mode) {
        return switch (mode) {
            case "WALK" -> 5;
            case "TRANSIT" -> 25;
            case "TWO_WHEELER" -> 30;
            default -> 35;
        };
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double radius = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return radius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
