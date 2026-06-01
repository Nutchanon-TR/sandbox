package com.sandbox.sandman.backend.repositories;

import com.sandbox.sandman.backend.model.entity.SearchRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SearchRunRepository extends JpaRepository<SearchRun, Long> {
    @Query("SELECT r FROM SearchRun r WHERE r.userId = :userId ORDER BY r.id DESC")
    List<SearchRun> findRecentByUserId(@Param("userId") Long userId, Pageable pageable);

    Optional<SearchRun> findByIdAndUserId(Long id, Long userId);
}
