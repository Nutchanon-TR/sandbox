package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.model.dto.JobTrackingDto;
import com.sandbox.sandman.backend.model.dto.TrackingUpdateRequest;
import com.sandbox.sandman.backend.model.entity.JobTracking;
import com.sandbox.sandman.backend.repositories.JobTrackingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrackingServiceTest {
    private final JobService jobService = mock(JobService.class);
    private final JobTrackingRepository trackingRepository = mock(JobTrackingRepository.class);
    private final JobjabMapper mapper = new JobjabMapper();
    private final TrackingService service = new TrackingService(jobService, trackingRepository, mapper);

    @Test
    void updateReusesExistingTrackingRowForSameUserAndJob() {
        JobTracking existing = new JobTracking();
        existing.setId(42L);
        existing.setUserId(7L);
        existing.setJobId(50L);
        existing.setStatus("INTERESTED");

        when(trackingRepository.findByUserIdAndJobId(7L, 50L)).thenReturn(Optional.of(existing));
        when(trackingRepository.save(any(JobTracking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobTrackingDto result = service.update(7L, 50L, new TrackingUpdateRequest("APPLIED", "Submitted resume"));

        ArgumentCaptor<JobTracking> captor = ArgumentCaptor.forClass(JobTracking.class);
        verify(jobService).find(50L);
        verify(trackingRepository).save(captor.capture());
        JobTracking saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(42L);
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getJobId()).isEqualTo(50L);
        assertThat(saved.getStatus()).isEqualTo("APPLIED");
        assertThat(saved.getAppliedAt()).isNotNull();
        assertThat(result.id()).isEqualTo(42L);
    }

    @Test
    void updateCreatesUserScopedTrackingRowWhenSameJobWasTrackedBySomeoneElse() {
        when(trackingRepository.findByUserIdAndJobId(7L, 50L)).thenReturn(Optional.empty());
        when(trackingRepository.save(any(JobTracking.class))).thenAnswer(invocation -> {
            JobTracking saved = invocation.getArgument(0);
            saved.setId(77L);
            return saved;
        });

        JobTrackingDto result = service.update(7L, 50L, new TrackingUpdateRequest("INTERESTED", "Worth checking"));

        ArgumentCaptor<JobTracking> captor = ArgumentCaptor.forClass(JobTracking.class);
        verify(jobService).find(50L);
        verify(trackingRepository).save(captor.capture());
        JobTracking saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getJobId()).isEqualTo(50L);
        assertThat(saved.getStatus()).isEqualTo("INTERESTED");
        assertThat(result.userId()).isEqualTo(7L);
    }
}
