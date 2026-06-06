package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.model.dto.SearchRunRequest;
import com.sandbox.sandman.backend.model.entity.SearchRun;
import com.sandbox.sandman.backend.model.entity.SearchRunEvent;
import com.sandbox.sandman.backend.model.entity.UserJobProfile;
import com.sandbox.sandman.backend.repositories.SearchRunEventRepository;
import com.sandbox.sandman.backend.repositories.SearchRunRepository;
import com.sandbox.sandman.backend.repositories.UserJobProfileRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SearchRunServiceTest {

    @Test
    void startUsesProfilePreferencesAsSearchFallbacks() {
        SearchRunRepository searchRunRepository = mock(SearchRunRepository.class);
        SearchRunEventRepository eventRepository = mock(SearchRunEventRepository.class);
        UserJobProfileRepository profileRepository = mock(UserJobProfileRepository.class);
        SearchRunWorkerService workerService = mock(SearchRunWorkerService.class);
        SearchRunService service = new SearchRunService(
                searchRunRepository,
                eventRepository,
                profileRepository,
                new JobjabMapper(),
                workerService
        );

        when(searchRunRepository.findFirstByUserIdAndStatusInOrderByIdDesc(eq(7L), anyList())).thenReturn(Optional.empty());
        UserJobProfile profile = new UserJobProfile();
        profile.setId(20L);
        profile.setUserId(7L);
        profile.setDesiredTitles(List.of("Frontend Engineer", "React Developer"));
        profile.setPreferredLocations(List.of("Bangkok", "Remote"));
        when(profileRepository.findByUserId(7L)).thenReturn(Optional.of(profile));
        when(searchRunRepository.save(any(SearchRun.class))).thenAnswer(invocation -> {
            SearchRun run = invocation.getArgument(0);
            run.setId(99L);
            return run;
        });

        service.start(7L, new SearchRunRequest(null, "  ", 99, null, List.of(" https://example.com/job "), List.of(), Map.of(), List.of("manual_jd")));

        ArgumentCaptor<SearchRun> runCaptor = ArgumentCaptor.forClass(SearchRun.class);
        verify(searchRunRepository).save(runCaptor.capture());
        SearchRun saved = runCaptor.getValue();
        assertThat(saved.getQuery()).isEqualTo("Frontend Engineer");
        assertThat(saved.getLocationText()).isEqualTo("Bangkok");
        assertThat(saved.getRequestedLimit()).isEqualTo(50);
        assertThat(saved.getCriteria())
                .containsEntry("query", "Frontend Engineer")
                .containsEntry("locationText", "Bangkok")
                .containsEntry("limit", 50);
        assertThat((Map<String, Boolean>) saved.getCriteria().get("profileDefaultsApplied"))
                .containsEntry("query", true)
                .containsEntry("locationText", true);

        ArgumentCaptor<SearchRunRequest> reqCaptor = ArgumentCaptor.forClass(SearchRunRequest.class);
        verify(workerService).execute(eq(99L), reqCaptor.capture());
        assertThat(reqCaptor.getValue().query()).isEqualTo("Frontend Engineer");
        assertThat(reqCaptor.getValue().locationText()).isEqualTo("Bangkok");
        assertThat(reqCaptor.getValue().limit()).isEqualTo(50);

        ArgumentCaptor<SearchRunEvent> eventCaptor = ArgumentCaptor.forClass(SearchRunEvent.class);
        verify(eventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getPayload()).containsKey("profileDefaultsApplied");
    }

    @Test
    void startDefaultsToUrlAndInferredOfficialSourcesWhenJobUrlIsProvidedWithoutSourceKeys() {
        SearchRunRepository searchRunRepository = mock(SearchRunRepository.class);
        SearchRunEventRepository eventRepository = mock(SearchRunEventRepository.class);
        UserJobProfileRepository profileRepository = mock(UserJobProfileRepository.class);
        SearchRunWorkerService workerService = mock(SearchRunWorkerService.class);
        SearchRunService service = new SearchRunService(
                searchRunRepository,
                eventRepository,
                profileRepository,
                new JobjabMapper(),
                workerService
        );
        when(profileRepository.findByUserId(7L)).thenReturn(Optional.empty());
        when(searchRunRepository.findFirstByUserIdAndStatusInOrderByIdDesc(eq(7L), anyList())).thenReturn(Optional.empty());
        when(searchRunRepository.save(any(SearchRun.class))).thenAnswer(invocation -> {
            SearchRun run = invocation.getArgument(0);
            run.setId(100L);
            return run;
        });

        service.start(7L, new SearchRunRequest(
                "engineer",
                null,
                10,
                null,
                List.of("https://boards.greenhouse.io/airbnb/jobs/123"),
                List.of(),
                Map.of(),
                List.of()
        ));

        ArgumentCaptor<SearchRun> runCaptor = ArgumentCaptor.forClass(SearchRun.class);
        verify(searchRunRepository).save(runCaptor.capture());
        assertThat(runCaptor.getValue().getSourceKeys())
                .containsExactly("user_url", "structured_data", "greenhouse");

        ArgumentCaptor<SearchRunRequest> reqCaptor = ArgumentCaptor.forClass(SearchRunRequest.class);
        verify(workerService).execute(eq(100L), reqCaptor.capture());
        assertThat(reqCaptor.getValue().sourceKeys())
                .containsExactly("user_url", "structured_data", "greenhouse");
    }

    @Test
    void startKeepsBroadDefaultsForQueryOnlySearchesSoConfiguredSourcesCanRun() {
        SearchRunRepository searchRunRepository = mock(SearchRunRepository.class);
        SearchRunEventRepository eventRepository = mock(SearchRunEventRepository.class);
        UserJobProfileRepository profileRepository = mock(UserJobProfileRepository.class);
        SearchRunWorkerService workerService = mock(SearchRunWorkerService.class);
        SearchRunService service = new SearchRunService(
                searchRunRepository,
                eventRepository,
                profileRepository,
                new JobjabMapper(),
                workerService
        );
        when(profileRepository.findByUserId(7L)).thenReturn(Optional.empty());
        when(searchRunRepository.findFirstByUserIdAndStatusInOrderByIdDesc(eq(7L), anyList())).thenReturn(Optional.empty());
        when(searchRunRepository.save(any(SearchRun.class))).thenAnswer(invocation -> {
            SearchRun run = invocation.getArgument(0);
            run.setId(101L);
            return run;
        });

        service.start(7L, new SearchRunRequest("react", "bangkok", 10, null, List.of(), List.of(), Map.of(), null));

        ArgumentCaptor<SearchRunRequest> reqCaptor = ArgumentCaptor.forClass(SearchRunRequest.class);
        verify(workerService).execute(eq(101L), reqCaptor.capture());
        assertThat(reqCaptor.getValue().sourceKeys())
                .containsExactly("manual_jd", "user_url", "greenhouse", "lever", "ashby", "structured_data", "sitemap");
    }

    @Test
    void startReusesExistingActiveRunInsteadOfCreatingDuplicateWork() {
        SearchRunRepository searchRunRepository = mock(SearchRunRepository.class);
        SearchRunEventRepository eventRepository = mock(SearchRunEventRepository.class);
        UserJobProfileRepository profileRepository = mock(UserJobProfileRepository.class);
        SearchRunWorkerService workerService = mock(SearchRunWorkerService.class);
        SearchRunService service = new SearchRunService(
                searchRunRepository,
                eventRepository,
                profileRepository,
                new JobjabMapper(),
                workerService
        );

        SearchRun active = new SearchRun();
        active.setId(200L);
        active.setUserId(7L);
        active.setStatus("RUNNING");
        active.setRequestedLimit(10);
        active.setSourceKeys(List.of("user_url"));
        when(searchRunRepository.findFirstByUserIdAndStatusInOrderByIdDesc(eq(7L), anyList())).thenReturn(Optional.of(active));

        var result = service.start(7L, new SearchRunRequest("new", null, 10, null, List.of(), List.of(), Map.of(), List.of()));

        assertThat(result.id()).isEqualTo(200L);
        assertThat(result.status()).isEqualTo("RUNNING");
        verify(searchRunRepository, never()).save(any(SearchRun.class));
        verify(workerService, never()).execute(anyLong(), any());
        verify(eventRepository).save(any(SearchRunEvent.class));
    }

    @Test
    void startMarksStaleActiveRunFailedBeforeCreatingNewRun() {
        SearchRunRepository searchRunRepository = mock(SearchRunRepository.class);
        SearchRunEventRepository eventRepository = mock(SearchRunEventRepository.class);
        UserJobProfileRepository profileRepository = mock(UserJobProfileRepository.class);
        SearchRunWorkerService workerService = mock(SearchRunWorkerService.class);
        SearchRunService service = new SearchRunService(
                searchRunRepository,
                eventRepository,
                profileRepository,
                new JobjabMapper(),
                workerService
        );

        SearchRun stale = new SearchRun();
        stale.setId(201L);
        stale.setUserId(7L);
        stale.setStatus("QUEUED");
        stale.setCreatedAt(ZonedDateTime.now().minusMinutes(31));
        stale.setRequestedLimit(10);
        stale.setSourceKeys(List.of("user_url"));
        when(searchRunRepository.findFirstByUserIdAndStatusInOrderByIdDesc(eq(7L), anyList())).thenReturn(Optional.of(stale));
        when(profileRepository.findByUserId(7L)).thenReturn(Optional.empty());
        when(searchRunRepository.save(any(SearchRun.class))).thenAnswer(invocation -> {
            SearchRun run = invocation.getArgument(0);
            if (run.getId() == null) run.setId(202L);
            return run;
        });

        var result = service.start(7L, new SearchRunRequest("react", null, 10, null, List.of(), List.of(), Map.of(), List.of("user_url")));

        assertThat(result.id()).isEqualTo(202L);
        assertThat(result.status()).isEqualTo("QUEUED");
        ArgumentCaptor<SearchRun> runCaptor = ArgumentCaptor.forClass(SearchRun.class);
        verify(searchRunRepository, times(2)).save(runCaptor.capture());
        assertThat(runCaptor.getAllValues().get(0).getId()).isEqualTo(201L);
        assertThat(runCaptor.getAllValues().get(0).getStatus()).isEqualTo("FAILED");
        assertThat(runCaptor.getAllValues().get(0).getCompletedAt()).isNotNull();
        assertThat(runCaptor.getAllValues().get(1).getId()).isEqualTo(202L);
        assertThat(runCaptor.getAllValues().get(1).getStatus()).isEqualTo("QUEUED");
        verify(workerService).execute(eq(202L), any(SearchRunRequest.class));

        ArgumentCaptor<SearchRunEvent> eventCaptor = ArgumentCaptor.forClass(SearchRunEvent.class);
        verify(eventRepository, times(2)).save(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues().get(0).getSearchRunId()).isEqualTo(201L);
        assertThat(eventCaptor.getAllValues().get(0).getMessage()).contains("Stale active JOBJAB search");
        assertThat(eventCaptor.getAllValues().get(1).getSearchRunId()).isEqualTo(202L);
        assertThat(eventCaptor.getAllValues().get(1).getMessage()).isEqualTo("JOBJAB search queued");
    }

    @Test
    void eventsRejectsRunsOwnedByAnotherUserBeforeLoadingEventRows() {
        SearchRunRepository searchRunRepository = mock(SearchRunRepository.class);
        SearchRunEventRepository eventRepository = mock(SearchRunEventRepository.class);
        UserJobProfileRepository profileRepository = mock(UserJobProfileRepository.class);
        SearchRunWorkerService workerService = mock(SearchRunWorkerService.class);
        SearchRunService service = new SearchRunService(
                searchRunRepository,
                eventRepository,
                profileRepository,
                new JobjabMapper(),
                workerService
        );

        when(searchRunRepository.findByIdAndUserId(123L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.events(7L, 123L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Search run not found");

        verify(eventRepository, never()).findBySearchRunIdOrderByIdAsc(anyLong());
    }
}
