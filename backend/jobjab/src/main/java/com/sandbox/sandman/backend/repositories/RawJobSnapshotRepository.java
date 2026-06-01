package com.sandbox.sandman.backend.repositories;

import com.sandbox.sandman.backend.model.entity.RawJobSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RawJobSnapshotRepository extends JpaRepository<RawJobSnapshot, Long> {
    Optional<RawJobSnapshot> findBySourceIdAndSourceJobKeyAndContentHash(Long sourceId, String sourceJobKey, String contentHash);
}
