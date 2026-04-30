package com.sandbox.sandman.backend.repositories;

import com.sandbox.sandman.backend.model.entity.AppUser;
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
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findBySupabaseUid(UUID supabaseUid);

    @Query("SELECT u FROM AppUser u WHERE LOWER(u.displayName) LIKE LOWER(CONCAT('%', :q, '%')) ORDER BY u.displayName ASC")
    List<AppUser> searchByDisplayName(@Param("q") String q);

    @Modifying
    @Query("UPDATE AppUser u SET u.lastSeenAt = :ts WHERE u.id = :id")
    void touchLastSeenAt(@Param("id") Long id, @Param("ts") ZonedDateTime ts);
}
