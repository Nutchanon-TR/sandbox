package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.adapters.NormalizedJobRecord;
import com.sandbox.sandman.backend.model.dto.JobDto;
import com.sandbox.sandman.backend.model.dto.PageResponse;
import com.sandbox.sandman.backend.model.entity.Job;
import com.sandbox.sandman.backend.model.entity.JobTracking;
import com.sandbox.sandman.backend.model.entity.RouteCache;
import com.sandbox.sandman.backend.model.entity.UserJobProfile;
import com.sandbox.sandman.backend.repositories.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JobServiceTest {
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobMatchRepository matchRepository = mock(JobMatchRepository.class);
    private final JobTrackingRepository trackingRepository = mock(JobTrackingRepository.class);
    private final RouteCacheRepository routeRepository = mock(RouteCacheRepository.class);
    private final UserJobProfileRepository profileRepository = mock(UserJobProfileRepository.class);
    private final JobService service = new JobService(
            jobRepository,
            matchRepository,
            trackingRepository,
            routeRepository,
            profileRepository,
            new JobjabMapper()
    );

    @Test
    void listCanSortNearbyFirstUsingCachedRoutesOnly() {
        Job far = job(1L, "Far role");
        Job near = job(2L, "Near role");
        UserJobProfile profile = new UserJobProfile();
        profile.setUserId(99L);
        profile.setTravelMode("DRIVE");

        when(jobRepository.findVisible(isNull(), any(Pageable.class))).thenReturn(List.of(far, near));
        when(profileRepository.findByUserId(99L)).thenReturn(Optional.of(profile));
        when(routeRepository.findByUserIdAndTravelModeOrderByCreatedAtDesc(99L, "DRIVE"))
                .thenReturn(List.of(route(1L, 45), route(2L, 12)));
        when(matchRepository.findByUserIdAndJobId(anyLong(), anyLong())).thenReturn(Optional.empty());
        when(trackingRepository.findByUserIdAndJobId(anyLong(), anyLong())).thenReturn(Optional.empty());

        PageResponse<JobDto> page = service.list(99L, null, 10, true);

        assertThat(page.items()).extracting(JobDto::title).containsExactly("Near role", "Far role");
        assertThat(page.items()).extracting(item -> item.route().durationSeconds()).containsExactly(12 * 60, 45 * 60);
    }

    @Test
    void listCanSortNearbyFirstUsingCoordinateEstimatesWhenNoRouteCacheExists() {
        Job far = job(1L, "Far role");
        far.setLocationLatitude(13.9000);
        far.setLocationLongitude(100.7000);
        Job near = job(2L, "Near role");
        near.setLocationLatitude(13.7370);
        near.setLocationLongitude(100.5600);
        UserJobProfile profile = new UserJobProfile();
        profile.setUserId(99L);
        profile.setHomeLatitude(13.7367);
        profile.setHomeLongitude(100.5231);
        profile.setHomeLocationLabel("Bangkok");
        profile.setTravelMode("DRIVE");

        when(jobRepository.findVisible(isNull(), any(Pageable.class))).thenReturn(List.of(far, near));
        when(profileRepository.findByUserId(99L)).thenReturn(Optional.of(profile));
        when(routeRepository.findByUserIdAndTravelModeOrderByCreatedAtDesc(99L, "DRIVE")).thenReturn(List.of());
        when(matchRepository.findByUserIdAndJobId(anyLong(), anyLong())).thenReturn(Optional.empty());
        when(trackingRepository.findByUserIdAndJobId(anyLong(), anyLong())).thenReturn(Optional.empty());

        PageResponse<JobDto> page = service.list(99L, null, 10, true);

        assertThat(page.items()).extracting(JobDto::title).containsExactly("Near role", "Far role");
        assertThat(page.items()).extracting(item -> item.route().provider()).containsExactly("ESTIMATE", "ESTIMATE");
        assertThat(page.items()).extracting(item -> item.route().durationSeconds()).doesNotContainNull();
        verify(routeRepository).findByUserIdAndTravelModeOrderByCreatedAtDesc(99L, "DRIVE");
    }

    @Test
    void nearbyFirstIncludesRoutedJobsOutsideRecentPage() {
        Job recentFar = job(1L, "Recent far role");
        Job olderNear = job(99L, "Older near role");
        UserJobProfile profile = new UserJobProfile();
        profile.setUserId(99L);
        profile.setTravelMode("DRIVE");

        when(profileRepository.findByUserId(99L)).thenReturn(Optional.of(profile));
        when(routeRepository.findByUserIdAndTravelModeOrderByCreatedAtDesc(99L, "DRIVE"))
                .thenReturn(List.of(route(99L, 8), route(1L, 45)));
        when(jobRepository.findVisibleByIds(anyList(), isNull())).thenReturn(List.of(olderNear, recentFar));
        when(jobRepository.findVisible(isNull(), any(Pageable.class))).thenReturn(List.of(recentFar));
        when(matchRepository.findByUserIdAndJobId(anyLong(), anyLong())).thenReturn(Optional.empty());
        when(trackingRepository.findByUserIdAndJobId(anyLong(), anyLong())).thenReturn(Optional.empty());

        PageResponse<JobDto> page = service.list(99L, null, 1, true);

        assertThat(page.items()).extracting(JobDto::title).containsExactly("Older near role");
        assertThat(page.items().get(0).route().durationSeconds()).isEqualTo(8 * 60);
        assertThat(page.hasMore()).isTrue();
    }

    @Test
    void listTrackedOnlyReturnsTrackedJobsInTrackingOrder() {
        Job older = job(1L, "Already applied");
        Job latest = job(2L, "Interview next");
        JobTracking latestTracking = tracking(2L, "INTERVIEW");
        JobTracking olderTracking = tracking(1L, "APPLIED");

        when(trackingRepository.findByUserIdOrderByUpdatedAtDesc(99L)).thenReturn(List.of(latestTracking, olderTracking));
        when(jobRepository.findVisibleByIds(eq(List.of(2L, 1L)), isNull())).thenReturn(List.of(older, latest));
        when(profileRepository.findByUserId(99L)).thenReturn(Optional.empty());
        when(matchRepository.findByUserIdAndJobId(anyLong(), anyLong())).thenReturn(Optional.empty());
        when(trackingRepository.findByUserIdAndJobId(99L, 2L)).thenReturn(Optional.of(latestTracking));
        when(trackingRepository.findByUserIdAndJobId(99L, 1L)).thenReturn(Optional.of(olderTracking));

        PageResponse<JobDto> page = service.list(99L, null, 10, false, true);

        assertThat(page.items()).extracting(JobDto::title).containsExactly("Interview next", "Already applied");
        assertThat(page.items()).extracting(item -> item.tracking().status()).containsExactly("INTERVIEW", "APPLIED");
    }

    @Test
    void trackedOnlyCanReturnMoreThanRegularJobListCapForKanbanBoard() {
        List<JobTracking> trackingRows = new ArrayList<>();
        List<Job> jobs = new ArrayList<>();
        for (long id = 1; id <= 60; id++) {
            trackingRows.add(tracking(id, "INTERESTED"));
            jobs.add(job(id, "Tracked role " + id));
            when(trackingRepository.findByUserIdAndJobId(99L, id)).thenReturn(Optional.of(tracking(id, "INTERESTED")));
        }

        when(trackingRepository.findByUserIdOrderByUpdatedAtDesc(99L)).thenReturn(trackingRows);
        when(jobRepository.findVisibleByIds(anyList(), isNull())).thenReturn(jobs);
        when(profileRepository.findByUserId(99L)).thenReturn(Optional.empty());
        when(matchRepository.findByUserIdAndJobId(anyLong(), anyLong())).thenReturn(Optional.empty());

        PageResponse<JobDto> page = service.list(99L, null, 60, false, true);

        assertThat(page.items()).hasSize(60);
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void trackedOnlyNearbyFirstUsesTrackedBoardLimitInsteadOfRegularNearbyCap() {
        List<JobTracking> trackingRows = new ArrayList<>();
        List<Job> jobs = new ArrayList<>();
        List<RouteCache> routes = new ArrayList<>();
        UserJobProfile profile = new UserJobProfile();
        profile.setUserId(99L);
        profile.setTravelMode("DRIVE");
        for (long id = 1; id <= 60; id++) {
            trackingRows.add(tracking(id, "INTERESTED"));
            jobs.add(job(id, "Tracked role " + id));
            routes.add(route(id, (int) id));
            when(trackingRepository.findByUserIdAndJobId(99L, id)).thenReturn(Optional.of(tracking(id, "INTERESTED")));
        }

        when(trackingRepository.findByUserIdOrderByUpdatedAtDesc(99L)).thenReturn(trackingRows);
        when(jobRepository.findVisibleByIds(anyList(), isNull())).thenReturn(jobs);
        when(profileRepository.findByUserId(99L)).thenReturn(Optional.of(profile));
        when(routeRepository.findByUserIdAndTravelModeOrderByCreatedAtDesc(99L, "DRIVE")).thenReturn(routes);
        when(matchRepository.findByUserIdAndJobId(anyLong(), anyLong())).thenReturn(Optional.empty());

        PageResponse<JobDto> page = service.list(99L, null, 60, true, true);

        assertThat(page.items()).hasSize(60);
        assertThat(page.items()).extracting(JobDto::title).containsExactly("Tracked role 1", "Tracked role 2", "Tracked role 3", "Tracked role 4", "Tracked role 5",
                "Tracked role 6", "Tracked role 7", "Tracked role 8", "Tracked role 9", "Tracked role 10", "Tracked role 11", "Tracked role 12", "Tracked role 13",
                "Tracked role 14", "Tracked role 15", "Tracked role 16", "Tracked role 17", "Tracked role 18", "Tracked role 19", "Tracked role 20", "Tracked role 21",
                "Tracked role 22", "Tracked role 23", "Tracked role 24", "Tracked role 25", "Tracked role 26", "Tracked role 27", "Tracked role 28", "Tracked role 29",
                "Tracked role 30", "Tracked role 31", "Tracked role 32", "Tracked role 33", "Tracked role 34", "Tracked role 35", "Tracked role 36", "Tracked role 37",
                "Tracked role 38", "Tracked role 39", "Tracked role 40", "Tracked role 41", "Tracked role 42", "Tracked role 43", "Tracked role 44", "Tracked role 45",
                "Tracked role 46", "Tracked role 47", "Tracked role 48", "Tracked role 49", "Tracked role 50", "Tracked role 51", "Tracked role 52", "Tracked role 53",
                "Tracked role 54", "Tracked role 55", "Tracked role 56", "Tracked role 57", "Tracked role 58", "Tracked role 59", "Tracked role 60");
    }

    @Test
    void getDoesNotExposeAnotherUsersMatchTrackingOrRoute() {
        Job job = job(10L, "Shared public role");
        UserJobProfile profile = new UserJobProfile();
        profile.setUserId(7L);
        profile.setTravelMode("DRIVE");

        when(jobRepository.findById(10L)).thenReturn(Optional.of(job));
        when(matchRepository.findByUserIdAndJobId(7L, 10L)).thenReturn(Optional.empty());
        when(trackingRepository.findByUserIdAndJobId(7L, 10L)).thenReturn(Optional.empty());
        when(profileRepository.findByUserId(7L)).thenReturn(Optional.of(profile));
        when(routeRepository.findByUserIdAndTravelModeOrderByCreatedAtDesc(7L, "DRIVE")).thenReturn(List.of());

        JobDto result = service.get(7L, 10L);

        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.match()).isNull();
        assertThat(result.tracking()).isNull();
        assertThat(result.route()).isNull();
    }

    @Test
    void upsertRefreshesLastSeenAndUnarchivesExistingJobWhenSeenAgain() {
        ZonedDateTime oldFirstSeen = ZonedDateTime.now().minusDays(10);
        ZonedDateTime oldLastSeen = ZonedDateTime.now().minusDays(3);
        ZonedDateTime archivedAt = ZonedDateTime.now().minusDays(1);
        Job existing = job(20L, "Frontend Engineer");
        existing.setSourceId(5L);
        existing.setSourceJobKey("greenhouse:123");
        existing.setFirstSeenAt(oldFirstSeen);
        existing.setLastSeenAt(oldLastSeen);
        existing.setArchivedAt(archivedAt);

        when(jobRepository.findBySourceIdAndSourceJobKey(5L, "greenhouse:123")).thenReturn(Optional.of(existing));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Job result = service.upsert(5L, 88L, normalized("greenhouse:123"));

        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).save(captor.capture());
        Job saved = captor.getValue();
        assertThat(result.getId()).isEqualTo(20L);
        assertThat(saved.getLatestSnapshotId()).isEqualTo(88L);
        assertThat(saved.getFirstSeenAt()).isEqualTo(oldFirstSeen);
        assertThat(saved.getLastSeenAt()).isAfter(oldLastSeen);
        assertThat(saved.getArchivedAt()).isNull();
    }

    private Job job(Long id, String title) {
        Job job = new Job();
        job.setId(id);
        job.setSourceId(1L);
        job.setSourceJobKey("job-" + id);
        job.setTitle(title);
        job.setCompany("Acme");
        job.setCurrency("THB");
        job.setDescription("");
        job.setSkills(List.of());
        job.setLastSeenAt(ZonedDateTime.now().minusMinutes(id));
        return job;
    }

    private RouteCache route(Long jobId, int minutes) {
        RouteCache route = new RouteCache();
        route.setUserId(99L);
        route.setJobId(jobId);
        route.setTravelMode("DRIVE");
        route.setDurationSeconds(minutes * 60);
        route.setProvider("GOOGLE_MAPS");
        route.setExpiresAt(ZonedDateTime.now().plusDays(1));
        return route;
    }

    private JobTracking tracking(Long jobId, String status) {
        JobTracking tracking = new JobTracking();
        tracking.setId(jobId + 100);
        tracking.setUserId(99L);
        tracking.setJobId(jobId);
        tracking.setStatus(status);
        return tracking;
    }

    private NormalizedJobRecord normalized(String sourceJobKey) {
        return new NormalizedJobRecord(
                sourceJobKey,
                "https://example.com/jobs/" + sourceJobKey,
                "Frontend Engineer",
                "Acme",
                "Bangkok",
                null,
                null,
                null,
                null,
                "THB",
                "Full-time",
                "Hybrid",
                List.of("React"),
                "Build product UI",
                "https://example.com/apply/" + sourceJobKey,
                null
        );
    }
}
