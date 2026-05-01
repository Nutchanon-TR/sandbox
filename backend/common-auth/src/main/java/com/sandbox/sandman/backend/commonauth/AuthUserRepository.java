package com.sandbox.sandman.backend.commonauth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthUserRepository extends JpaRepository<AuthUser, Long> {

    Optional<AuthUser> findBySupabaseUid(UUID supabaseUid);

    @Query("SELECT u FROM AuthUser u WHERE LOWER(u.displayName) LIKE LOWER(CONCAT('%', :q, '%')) ORDER BY u.displayName ASC")
    List<AuthUser> searchByDisplayName(@Param("q") String q);

    @Modifying
    @Query("UPDATE AuthUser u SET u.lastSeenAt = :ts WHERE u.id = :id")
    void touchLastSeenAt(@Param("id") Long id, @Param("ts") ZonedDateTime ts);
}
