package com.sandbox.sandman.backend.repositories;

import com.sandbox.sandman.backend.model.entity.JobSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JobSourceRepository extends JpaRepository<JobSource, Long> {
    Optional<JobSource> findBySourceKey(String sourceKey);
}
