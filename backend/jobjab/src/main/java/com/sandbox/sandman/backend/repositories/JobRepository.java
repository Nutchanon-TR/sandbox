package com.sandbox.sandman.backend.repositories;

import com.sandbox.sandman.backend.model.entity.Job;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {
    Optional<Job> findBySourceIdAndSourceJobKey(Long sourceId, String sourceJobKey);

    @Query("""
        SELECT j FROM Job j
        WHERE j.archivedAt IS NULL
          AND (:q IS NULL OR LOWER(j.title) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(j.company) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(j.locationText) LIKE LOWER(CONCAT('%', :q, '%')))
        ORDER BY j.lastSeenAt DESC, j.id DESC
        """)
    List<Job> findVisible(@Param("q") String q, Pageable pageable);

    @Query("""
        SELECT j FROM Job j
        WHERE j.archivedAt IS NULL
          AND j.id IN :ids
          AND (:q IS NULL OR LOWER(j.title) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(j.company) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(j.locationText) LIKE LOWER(CONCAT('%', :q, '%')))
        """)
    List<Job> findVisibleByIds(@Param("ids") List<Long> ids, @Param("q") String q);
}
