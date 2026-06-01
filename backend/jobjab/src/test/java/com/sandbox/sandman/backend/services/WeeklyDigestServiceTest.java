package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.model.entity.*;
import com.sandbox.sandman.backend.repositories.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeeklyDigestServiceTest {
    private final WeeklyDigestRepository digestRepository = mock(WeeklyDigestRepository.class);
    private final WeeklyDigestItemRepository itemRepository = mock(WeeklyDigestItemRepository.class);
    private final JobMatchRepository matchRepository = mock(JobMatchRepository.class);
    private final JobTrackingRepository trackingRepository = mock(JobTrackingRepository.class);
    private final UserJobProfileRepository profileRepository = mock(UserJobProfileRepository.class);
    private final RouteCacheRepository routeCacheRepository = mock(RouteCacheRepository.class);
    private final JobService jobService = mock(JobService.class);
    private final JobjabMapper mapper = new JobjabMapper();

    private final WeeklyDigestService service = new WeeklyDigestService(
            digestRepository,
            itemRepository,
            matchRepository,
            trackingRepository,
            profileRepository,
            routeCacheRepository,
            jobService,
            mapper
    );

    @Test
    void generateExcludesCachedRoutesOverMaxCommute() {
        UserJobProfile profile = new UserJobProfile();
        profile.setId(10L);
        profile.setUserId(1L);
        profile.setTravelMode("DRIVE");
        profile.setMaxCommuteMinutes(30);

        JobMatch farMatch = match(100L, 90, "Strong skill match.");
        JobMatch nearMatch = match(200L, 80, "Good fit.");
        RouteCache farRoute = route(100L, 60);
        RouteCache nearRoute = route(200L, 20);

        when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(digestRepository.findByUserIdAndWeekStart(eq(1L), any(LocalDate.class))).thenReturn(Optional.empty());
        when(matchRepository.findByUserIdOrderByMatchScoreDescAnalyzedAtDesc(1L)).thenReturn(List.of(farMatch, nearMatch));
        when(trackingRepository.findByUserIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of());
        when(routeCacheRepository.findByUserIdAndTravelModeOrderByCreatedAtDesc(1L, "DRIVE")).thenReturn(List.of(farRoute, nearRoute));
        when(digestRepository.save(any(WeeklyDigest.class))).thenAnswer(invocation -> {
            WeeklyDigest digest = invocation.getArgument(0);
            digest.setId(99L);
            return digest;
        });
        when(itemRepository.findByWeeklyDigestIdOrderByRankAsc(99L)).thenReturn(List.of());

        service.generate(1L);

        ArgumentCaptor<WeeklyDigestItem> itemCaptor = ArgumentCaptor.forClass(WeeklyDigestItem.class);
        verify(itemRepository).save(itemCaptor.capture());
        WeeklyDigestItem saved = itemCaptor.getValue();
        assertThat(saved.getJobId()).isEqualTo(200L);
        assertThat(saved.getReason()).contains("Commute: 20 min");
    }

    @Test
    void generateMarksDigestAsSentForInAppDelivery() {
        when(profileRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(digestRepository.findByUserIdAndWeekStart(eq(1L), any(LocalDate.class))).thenReturn(Optional.empty());
        when(matchRepository.findByUserIdOrderByMatchScoreDescAnalyzedAtDesc(1L)).thenReturn(List.of());
        when(trackingRepository.findByUserIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of());
        when(digestRepository.save(any(WeeklyDigest.class))).thenAnswer(invocation -> {
            WeeklyDigest digest = invocation.getArgument(0);
            digest.setId(100L);
            return digest;
        });
        when(itemRepository.findByWeeklyDigestIdOrderByRankAsc(100L)).thenReturn(List.of());

        service.generate(1L);

        ArgumentCaptor<WeeklyDigest> digestCaptor = ArgumentCaptor.forClass(WeeklyDigest.class);
        verify(digestRepository).save(digestCaptor.capture());
        WeeklyDigest saved = digestCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo("SENT");
        assertThat(saved.getSentAt()).isNotNull();
        assertThat(saved.getSummary()).contains("No matched jobs yet");
    }

    @Test
    void generateSkipsJobsThatAlreadyMovedPastInterested() {
        JobMatch appliedMatch = match(100L, 95, "Already applied.");
        JobMatch interestedMatch = match(200L, 85, "Still worth applying.");

        JobTracking appliedTracking = tracking(100L, "APPLIED");
        JobTracking interestedTracking = tracking(200L, "INTERESTED");

        when(profileRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(digestRepository.findByUserIdAndWeekStart(eq(1L), any(LocalDate.class))).thenReturn(Optional.empty());
        when(matchRepository.findByUserIdOrderByMatchScoreDescAnalyzedAtDesc(1L)).thenReturn(List.of(appliedMatch, interestedMatch));
        when(trackingRepository.findByUserIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of(appliedTracking, interestedTracking));
        when(digestRepository.save(any(WeeklyDigest.class))).thenAnswer(invocation -> {
            WeeklyDigest digest = invocation.getArgument(0);
            digest.setId(101L);
            return digest;
        });
        when(itemRepository.findByWeeklyDigestIdOrderByRankAsc(101L)).thenReturn(List.of());

        service.generate(1L);

        ArgumentCaptor<WeeklyDigestItem> itemCaptor = ArgumentCaptor.forClass(WeeklyDigestItem.class);
        verify(itemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getJobId()).isEqualTo(200L);
    }

    private JobMatch match(Long jobId, Integer score, String summary) {
        JobMatch match = new JobMatch();
        match.setId(jobId + 1);
        match.setUserId(1L);
        match.setJobId(jobId);
        match.setMatchScore(score);
        match.setAiSummary(summary);
        match.setAnalyzedAt(ZonedDateTime.now());
        return match;
    }

    private RouteCache route(Long jobId, int minutes) {
        RouteCache route = new RouteCache();
        route.setId(jobId + 2);
        route.setUserId(1L);
        route.setJobId(jobId);
        route.setTravelMode("DRIVE");
        route.setDurationSeconds(minutes * 60);
        route.setProvider("GOOGLE_MAPS");
        route.setCreatedAt(ZonedDateTime.now());
        route.setExpiresAt(ZonedDateTime.now().plusDays(1));
        return route;
    }

    private JobTracking tracking(Long jobId, String status) {
        JobTracking tracking = new JobTracking();
        tracking.setId(jobId + 3);
        tracking.setUserId(1L);
        tracking.setJobId(jobId);
        tracking.setStatus(status);
        return tracking;
    }
}
