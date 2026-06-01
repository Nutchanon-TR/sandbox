package com.sandbox.sandman.backend.repositories;

import com.sandbox.sandman.backend.model.entity.JobTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobTrackingRepository extends JpaRepository<JobTracking, Long> {
    Optional<JobTracking> findByUserIdAndJobId(Long userId, Long jobId);

    List<JobTracking> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
