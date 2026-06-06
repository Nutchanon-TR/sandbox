package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.adapters.JobFetchMode;
import com.sandbox.sandman.backend.adapters.JobSourceAdapter;
import com.sandbox.sandman.backend.model.dto.JobSourceDto;
import com.sandbox.sandman.backend.model.entity.JobSource;
import com.sandbox.sandman.backend.repositories.JobSourceRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobSourceServiceTest {

    @Test
    void listSourcesIncludesPlatformTargetHints() {
        JobSourceAdapter adapter = mock(JobSourceAdapter.class);
        when(adapter.sourceKey()).thenReturn("greenhouse");
        when(adapter.fetchMode()).thenReturn(JobFetchMode.OFFICIAL_API);

        JobSource source = new JobSource();
        source.setId(1L);
        source.setSourceKey("greenhouse");
        source.setDisplayName("Greenhouse");
        source.setFetchMode("OFFICIAL_API");
        source.setAdapterConfig(Map.of());

        JobSourceRepository repository = mock(JobSourceRepository.class);
        when(repository.findBySourceKey("greenhouse")).thenReturn(Optional.of(source));
        when(repository.findAll()).thenReturn(List.of(source));

        JobSourceService service = new JobSourceService(repository, List.of(adapter));

        List<JobSourceDto> result = service.listSources();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).targetHint()).isEqualTo("Greenhouse board token");
        assertThat(result.get(0).targetExample()).isEqualTo("airbnb");
        assertThat(result.get(0).acceptsPerSearchTargets()).isTrue();
    }

    @Test
    void partnerApprovalSourceIsNotSearchableEvenWhenConfigured() {
        JobSourceAdapter adapter = mock(JobSourceAdapter.class);
        when(adapter.sourceKey()).thenReturn("greenhouse");
        when(adapter.fetchMode()).thenReturn(JobFetchMode.OFFICIAL_API);

        JobSource source = new JobSource();
        source.setId(1L);
        source.setSourceKey("greenhouse");
        source.setDisplayName("Greenhouse");
        source.setFetchMode("OFFICIAL_API");
        source.setEnabled(true);
        source.setRequiresPartnerApproval(true);
        source.setAdapterConfig(Map.of("boards", List.of("airbnb")));

        JobSourceRepository repository = mock(JobSourceRepository.class);
        when(repository.findBySourceKey("greenhouse")).thenReturn(Optional.of(source));
        when(repository.findAll()).thenReturn(List.of(source));

        JobSourceService service = new JobSourceService(repository, List.of(adapter));

        JobSourceDto result = service.listSources().get(0);

        assertThat(result.configured()).isTrue();
        assertThat(result.searchable()).isFalse();
        assertThat(result.guidance()).isEqualTo("Needs partner/API approval before JOBJAB can fetch this platform.");
    }
}
