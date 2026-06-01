package com.sandbox.sandman.backend.repositories;

import com.sandbox.sandman.backend.model.entity.UserJobProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserJobProfileRepository extends JpaRepository<UserJobProfile, Long> {
    Optional<UserJobProfile> findByUserId(Long userId);
    List<UserJobProfile> findByWeeklyDigestEnabledTrue();
}
