package com.sandbox.sandman.backend.repositories;

import com.sandbox.sandman.backend.model.entity.WeeklyDigest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WeeklyDigestRepository extends JpaRepository<WeeklyDigest, Long> {
    List<WeeklyDigest> findByUserIdOrderByWeekStartDescCreatedAtDesc(Long userId);
    Optional<WeeklyDigest> findByUserIdAndWeekStart(Long userId, LocalDate weekStart);
}
