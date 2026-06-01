package com.sandbox.sandman.backend.repositories;

import com.sandbox.sandman.backend.model.entity.WeeklyDigestItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WeeklyDigestItemRepository extends JpaRepository<WeeklyDigestItem, Long> {
    List<WeeklyDigestItem> findByWeeklyDigestIdOrderByRankAsc(Long weeklyDigestId);
    void deleteByWeeklyDigestId(Long weeklyDigestId);
}
