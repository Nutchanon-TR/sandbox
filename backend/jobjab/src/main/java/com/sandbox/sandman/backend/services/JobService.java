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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobService {
    private final JobRepository jobRepository;
    private final JobMatchRepository jobMatchRepository;
    private final JobTrackingRepository jobTrackingRepository;
    private final RouteCacheRepository routeCacheRepository;
    private final UserJobProfileRepository profileRepository;
    private final JobjabMapper mapper;

    @Transactional
    public Job upsert(Long sourceId, Long snapshotId, NormalizedJobRecord normalized) {
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
        List<Job> jobs = trackedOnly
                ? trackedJobs(userId, blankToNull(q), fetchLimit)
                : jobRepository.findVisible(blankToNull(q), PageRequest.of(0, fetchLimit));
        Map<Long, RouteCache> routes = routeByJob(userId);
        if (nearbyFirst && !routes.isEmpty()) {
            jobs = jobs.stream().sorted(nearbyComparator(routes)).toList();
        }
        boolean hasMore = jobs.size() > safeLimit;
        List<Job> page = hasMore ? jobs.subList(0, safeLimit) : jobs;
        List<JobDto> items = page.stream().map(job -> mapper.toDto(job, match(userId, job.getId()), tracking(userId, job.getId()), routes.get(job.getId()))).toList();
        Long nextCursor = hasMore && !page.isEmpty() ? page.get(page.size() - 1).getId() : null;
        return new PageResponse<>(items, hasMore, nextCursor);
    }

    @Transactional(readOnly = true)
    public JobDto get(Long userId, Long jobId) {
        Job job = find(jobId);
        return mapper.toDto(job, match(userId, jobId), tracking(userId, jobId), routeByJob(userId).get(jobId));
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

    private Map<Long, RouteCache> routeByJob(Long userId) {
        UserJobProfile profile = profileRepository.findByUserId(userId).orElse(null);
        if (profile == null || profile.getTravelMode() == null || profile.getTravelMode().isBlank()) return Map.of();
        ZonedDateTime now = ZonedDateTime.now();
        return routeCacheRepository.findByUserIdAndTravelModeOrderByCreatedAtDesc(userId, profile.getTravelMode()).stream()
                .filter(route -> route.getExpiresAt() == null || route.getExpiresAt().isAfter(now))
                .collect(Collectors.toMap(RouteCache::getJobId, Function.identity(), (latest, ignored) -> latest));
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
}
