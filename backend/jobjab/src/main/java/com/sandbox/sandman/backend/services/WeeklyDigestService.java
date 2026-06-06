package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.model.dto.JobDto;
import com.sandbox.sandman.backend.model.dto.PageResponse;
import com.sandbox.sandman.backend.model.dto.WeeklyDigestDto;
import com.sandbox.sandman.backend.model.dto.WeeklyDigestItemDto;
import com.sandbox.sandman.backend.model.entity.*;
import com.sandbox.sandman.backend.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WeeklyDigestService {
    private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    private static final int DIGEST_LIMIT = 10;
    private static final int BACKFILL_SCAN_LIMIT = 50;
    private static final Set<String> EXCLUDED_TRACKING_STATUSES = Set.of("APPLIED", "INTERVIEW", "OFFER", "REJECTED", "ARCHIVED");

    private final WeeklyDigestRepository digestRepository;
    private final WeeklyDigestItemRepository itemRepository;
    private final JobMatchRepository matchRepository;
    private final JobTrackingRepository trackingRepository;
    private final UserJobProfileRepository profileRepository;
    private final RouteCacheRepository routeCacheRepository;
    private final JobService jobService;
    private final MatchAnalysisService matchAnalysisService;
    private final JobjabMapper mapper;

    @Transactional(readOnly = true)
    public List<WeeklyDigestDto> list(Long userId) {
        return digestRepository.findByUserIdOrderByWeekStartDescCreatedAtDesc(userId).stream()
                .map(digest -> toDto(userId, digest))
                .toList();
    }

    @Transactional
    public WeeklyDigestDto generate(Long userId) {
        UserJobProfile profile = profileRepository.findByUserId(userId).orElse(null);
        LocalDate weekStart = LocalDate.now(BANGKOK).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        WeeklyDigest digest = digestRepository.findByUserIdAndWeekStart(userId, weekStart).orElseGet(WeeklyDigest::new);
        digest.setUserId(userId);
        digest.setProfileId(profile == null ? null : profile.getId());
        digest.setWeekStart(weekStart);
        digest.setWeekEnd(weekEnd);
        if (digest.getId() != null) {
            itemRepository.deleteByWeeklyDigestId(digest.getId());
        }

        Map<Long, JobTracking> trackingByJob = trackingRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .collect(Collectors.toMap(JobTracking::getJobId, Function.identity(), (a, b) -> a));
        Map<Long, RouteCache> routeByJob = routeByJob(userId, profile);
        List<DigestCandidate> topMatches = candidateMatches(userId, trackingByJob, routeByJob, profile).stream()
                .filter(match -> {
                    JobTracking tracking = trackingByJob.get(match.getJobId());
                    return tracking == null || !EXCLUDED_TRACKING_STATUSES.contains(tracking.getStatus());
                })
                .map(match -> new DigestCandidate(match, routeByJob.get(match.getJobId())))
                .filter(candidate -> withinCommute(profile, candidate.route()))
                .sorted(candidateComparator())
                .limit(DIGEST_LIMIT)
                .toList();

        digest.setSummary(summary(topMatches));
        digest.setStatus("SENT");
        digest.setSentAt(ZonedDateTime.now(BANGKOK));
        digest = digestRepository.save(digest);

        int rank = 1;
        for (DigestCandidate candidate : topMatches) {
            JobMatch match = candidate.match();
            WeeklyDigestItem item = new WeeklyDigestItem();
            item.setWeeklyDigestId(digest.getId());
            item.setJobId(match.getJobId());
            item.setMatchId(match.getId());
            item.setRank(rank++);
            item.setReason(reason(match, candidate.route()));
            itemRepository.save(item);
        }

        return toDto(userId, digest);
    }

    private List<JobMatch> candidateMatches(Long userId, Map<Long, JobTracking> trackingByJob, Map<Long, RouteCache> routeByJob, UserJobProfile profile) {
        Map<Long, JobMatch> byJob = new LinkedHashMap<>();
        matchRepository.findByUserIdOrderByMatchScoreDescAnalyzedAtDesc(userId)
                .forEach(match -> {
                    if (match.getJobId() != null) byJob.putIfAbsent(match.getJobId(), match);
                });
        if (byJob.size() >= DIGEST_LIMIT) {
            return new ArrayList<>(byJob.values());
        }

        PageResponse<JobDto> recentJobs = jobService.list(userId, null, BACKFILL_SCAN_LIMIT, true);
        List<JobDto> jobs = recentJobs == null || recentJobs.items() == null ? List.of() : recentJobs.items();
        for (JobDto job : jobs) {
            if (job.id() == null || byJob.containsKey(job.id())) continue;
            JobTracking tracking = trackingByJob.get(job.id());
            if (tracking != null && EXCLUDED_TRACKING_STATUSES.contains(tracking.getStatus())) continue;
            if (!withinCommute(profile, routeByJob.get(job.id()))) continue;
            JobMatch match = matchAnalysisService.analyzeHeuristicOnly(userId, job.id());
            byJob.put(job.id(), match);
            if (byJob.size() >= DIGEST_LIMIT) break;
        }
        return new ArrayList<>(byJob.values());
    }

    private String summary(List<DigestCandidate> candidates) {
        if (candidates.isEmpty()) {
            return "No matched jobs yet. Run an on-demand search and analyze jobs first.";
        }
        int best = candidates.stream().map(DigestCandidate::match).map(JobMatch::getMatchScore).filter(score -> score != null).max(Integer::compareTo).orElse(0);
        long commuteAware = candidates.stream().filter(candidate -> candidate.route() != null && candidate.route().getDurationSeconds() != null).count();
        return "Top " + candidates.size() + " JOBJAB matches for this week. Best match score: " + best
                + "/100. Commute-aware picks: " + commuteAware + ".";
    }

    private Map<Long, RouteCache> routeByJob(Long userId, UserJobProfile profile) {
        if (profile == null || profile.getTravelMode() == null || profile.getTravelMode().isBlank()) {
            return Map.of();
        }
        ZonedDateTime now = ZonedDateTime.now();
        return routeCacheRepository.findByUserIdAndTravelModeOrderByCreatedAtDesc(userId, profile.getTravelMode()).stream()
                .filter(route -> route.getExpiresAt() == null || route.getExpiresAt().isAfter(now))
                .collect(Collectors.toMap(RouteCache::getJobId, Function.identity(), (latest, ignored) -> latest));
    }

    private boolean withinCommute(UserJobProfile profile, RouteCache route) {
        if (profile == null || profile.getMaxCommuteMinutes() == null || route == null || route.getDurationSeconds() == null) {
            return true;
        }
        return route.getDurationSeconds() <= profile.getMaxCommuteMinutes() * 60;
    }

    private Comparator<DigestCandidate> candidateComparator() {
        return Comparator
                .comparing((DigestCandidate candidate) -> candidate.match().getMatchScore(), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(candidate -> candidate.route() == null || candidate.route().getDurationSeconds() == null ? Integer.MAX_VALUE : candidate.route().getDurationSeconds())
                .thenComparing(candidate -> candidate.match().getAnalyzedAt(), Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private String reason(JobMatch match, RouteCache route) {
        String commute = route == null || route.getDurationSeconds() == null
                ? null
                : "Commute: " + Math.max(1, Math.round(route.getDurationSeconds() / 60.0)) + " min via " + route.getProvider() + ".";
        if (match.getAiSummary() == null || match.getAiSummary().isBlank()) {
            return commute == null ? null : commute;
        }
        return commute == null ? match.getAiSummary() : match.getAiSummary() + " " + commute;
    }

    private WeeklyDigestDto toDto(Long userId, WeeklyDigest digest) {
        List<WeeklyDigestItemDto> items = itemRepository.findByWeeklyDigestIdOrderByRankAsc(digest.getId()).stream()
                .map(item -> {
                    JobDto job = jobService.get(userId, item.getJobId());
                    return new WeeklyDigestItemDto(
                            item.getId(),
                            item.getWeeklyDigestId(),
                            item.getJobId(),
                            item.getMatchId(),
                            item.getRank(),
                            item.getReason(),
                            job
                    );
                })
                .toList();
        return new WeeklyDigestDto(
                digest.getId(),
                digest.getUserId(),
                digest.getProfileId(),
                digest.getStatus(),
                digest.getWeekStart(),
                digest.getWeekEnd(),
                digest.getSummary(),
                digest.getCreatedAt(),
                digest.getSentAt(),
                items
        );
    }

    private record DigestCandidate(JobMatch match, RouteCache route) {
    }
}
