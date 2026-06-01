package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.model.dto.JobTrackingDto;
import com.sandbox.sandman.backend.model.dto.TrackingUpdateRequest;
import com.sandbox.sandman.backend.model.entity.JobTracking;
import com.sandbox.sandman.backend.model.enums.JobTrackingStatus;
import com.sandbox.sandman.backend.repositories.JobTrackingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TrackingService {
    private final JobService jobService;
    private final JobTrackingRepository trackingRepository;
    private final JobjabMapper mapper;

    @Transactional(readOnly = true)
    public List<JobTrackingDto> list(Long userId) {
        return trackingRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream().map(mapper::toDto).toList();
    }

    @Transactional
    public JobTrackingDto update(Long userId, Long jobId, TrackingUpdateRequest req) {
        jobService.find(jobId);
        JobTrackingStatus status = parseStatus(req.status());
        JobTracking tracking = trackingRepository.findByUserIdAndJobId(userId, jobId).orElseGet(JobTracking::new);
        tracking.setUserId(userId);
        tracking.setJobId(jobId);
        tracking.setStatus(status.name());
        tracking.setNotes(req.notes());
        if (status == JobTrackingStatus.APPLIED && tracking.getAppliedAt() == null) {
            tracking.setAppliedAt(ZonedDateTime.now());
        }
        if (status == JobTrackingStatus.INTERVIEW && tracking.getInterviewAt() == null) {
            tracking.setInterviewAt(ZonedDateTime.now());
        }
        return mapper.toDto(trackingRepository.save(tracking));
    }

    private JobTrackingStatus parseStatus(String value) {
        if (value == null || value.isBlank()) return JobTrackingStatus.INTERESTED;
        try {
            return JobTrackingStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported tracking status: " + value);
        }
    }
}
