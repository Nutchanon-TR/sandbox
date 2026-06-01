package com.sandbox.sandman.backend.repositories;

import com.sandbox.sandman.backend.model.entity.RouteCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RouteCacheRepository extends JpaRepository<RouteCache, Long> {
    Optional<RouteCache> findByCacheKey(String cacheKey);
    List<RouteCache> findByUserIdAndTravelModeOrderByCreatedAtDesc(Long userId, String travelMode);
}
