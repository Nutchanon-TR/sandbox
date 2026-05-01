package com.sandbox.sandman.backend.commonauth;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Read-only view onto chat_app.users — the shared identity table populated by user-service.
 *
 * Other services may keep their own JPA entity mapped to the same table for domain-specific
 * needs; this class is the canonical entry-point for auth/identity resolution.
 */
@Entity
@Table(name = "users", schema = "chat_app")
@Data
@NoArgsConstructor
public class AuthUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supabase_uid", nullable = false, unique = true)
    private UUID supabaseUid;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @Column(name = "last_seen_at")
    private ZonedDateTime lastSeenAt;
}
