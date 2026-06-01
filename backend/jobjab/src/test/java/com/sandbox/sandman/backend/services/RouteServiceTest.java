package com.sandbox.sandman.backend.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sandbox.sandman.backend.model.dto.RouteDto;
import com.sandbox.sandman.backend.model.dto.RouteRequest;
import com.sandbox.sandman.backend.model.entity.Job;
import com.sandbox.sandman.backend.model.entity.RouteCache;
import com.sandbox.sandman.backend.model.entity.UserJobProfile;
import com.sandbox.sandman.backend.repositories.RouteCacheRepository;
import com.sandbox.sandman.backend.repositories.UserJobProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RouteServiceTest {
    private final JobService jobService = mock(JobService.class);
    private final UserJobProfileRepository profileRepository = mock(UserJobProfileRepository.class);
    private final RouteCacheRepository routeCacheRepository = mock(RouteCacheRepository.class);
    private final JobjabMapper mapper = new JobjabMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RouteService service = new RouteService(jobService, profileRepository, routeCacheRepository, mapper, objectMapper);

    @Test
    void computeReturnsFreshCachedRouteBeforeCreatingANewRoute() {
        Job job = new Job();
        job.setId(50L);
        job.setLocationText("Bangkok");

        UserJobProfile profile = new UserJobProfile();
        profile.setUserId(7L);
        profile.setTravelMode("DRIVE");
        profile.setHomeLocationLabel("Asok, Bangkok");

        RouteCache cached = new RouteCache();
        cached.setId(99L);
        cached.setUserId(7L);
        cached.setJobId(50L);
        cached.setTravelMode("DRIVE");
        cached.setOriginLabel("Asok, Bangkok");
        cached.setDestinationLabel("Bangkok");
        cached.setDistanceMeters(9000);
        cached.setDurationSeconds(1800);
        cached.setProvider("GOOGLE_MAPS");
        cached.setCreatedAt(ZonedDateTime.now().minusHours(1));
        cached.setExpiresAt(ZonedDateTime.now().plusDays(1));

        when(jobService.find(50L)).thenReturn(job);
        when(profileRepository.findByUserId(7L)).thenReturn(Optional.of(profile));
        when(routeCacheRepository.findByCacheKey(any())).thenReturn(Optional.of(cached));

        RouteDto result = service.compute(7L, 50L, new RouteRequest(null));

        assertThat(result.id()).isEqualTo(99L);
        assertThat(result.provider()).isEqualTo("GOOGLE_MAPS");
        assertThat(result.durationSeconds()).isEqualTo(1800);
        verify(routeCacheRepository, never()).save(any(RouteCache.class));
    }

    @Test
    void computeUsesGoogleRoutesWhenOnlyAddressLabelsAreAvailable() throws Exception {
        Job job = new Job();
        job.setId(60L);
        job.setLocationText("Central World, Bangkok");

        UserJobProfile profile = new UserJobProfile();
        profile.setUserId(7L);
        profile.setTravelMode("TRANSIT");
        profile.setHomeLocationLabel("BTS Asok, Bangkok");

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"routes":[{"distanceMeters":6500,"duration":"1500s"}]}
                """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        when(jobService.find(60L)).thenReturn(job);
        when(profileRepository.findByUserId(7L)).thenReturn(Optional.of(profile));
        when(routeCacheRepository.findByCacheKey(any())).thenReturn(Optional.empty());
        when(routeCacheRepository.save(any(RouteCache.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ReflectionTestUtils.setField(service, "googleMapsApiKey", "test-key");
        ReflectionTestUtils.setField(service, "httpClient", httpClient);

        RouteDto result = service.compute(7L, 60L, new RouteRequest(null));

        assertThat(result.provider()).isEqualTo("GOOGLE_MAPS");
        assertThat(result.distanceMeters()).isEqualTo(6500);
        assertThat(result.durationSeconds()).isEqualTo(1500);
        verify(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void computeRefreshesExpiredRouteCacheInsteadOfCreatingDuplicateCacheKey() {
        Job job = new Job();
        job.setId(70L);
        job.setLocationText("Central World, Bangkok");
        job.setLocationLatitude(13.7466);
        job.setLocationLongitude(100.5392);

        UserJobProfile profile = new UserJobProfile();
        profile.setUserId(7L);
        profile.setTravelMode("DRIVE");
        profile.setHomeLocationLabel("BTS Asok, Bangkok");
        profile.setHomeLatitude(13.7373);
        profile.setHomeLongitude(100.5607);

        RouteCache expired = new RouteCache();
        expired.setId(101L);
        expired.setUserId(7L);
        expired.setJobId(70L);
        expired.setCacheKey("same-key");
        expired.setTravelMode("DRIVE");
        expired.setDistanceMeters(1);
        expired.setDurationSeconds(1);
        expired.setProvider("GOOGLE_MAPS");
        expired.setExpiresAt(ZonedDateTime.now().minusMinutes(1));

        when(jobService.find(70L)).thenReturn(job);
        when(profileRepository.findByUserId(7L)).thenReturn(Optional.of(profile));
        when(routeCacheRepository.findByCacheKey(any())).thenReturn(Optional.of(expired));
        when(routeCacheRepository.save(any(RouteCache.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RouteDto result = service.compute(7L, 70L, new RouteRequest(null));

        assertThat(result.id()).isEqualTo(101L);
        assertThat(result.provider()).isEqualTo("ESTIMATE");
        assertThat(result.distanceMeters()).isGreaterThan(1);
        assertThat(result.durationSeconds()).isGreaterThan(1);
        verify(routeCacheRepository).save(expired);
    }
}
