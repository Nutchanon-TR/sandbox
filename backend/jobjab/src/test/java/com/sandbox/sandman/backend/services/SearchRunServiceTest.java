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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
}
