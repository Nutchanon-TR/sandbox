package com.sandbox.sandman.backend.repositories;

import com.sandbox.sandman.backend.model.entity.JobMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobMatchRepository extends JpaRepository<JobMatch, Long> {
    Optional<JobMatch> findByUserIdAndJobId(Long userId, Long jobId);

    List<JobMatch> findByUserIdOrderByMatchScoreDescAnalyzedAtDesc(Long userId);
}
