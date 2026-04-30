package com.sandbox.sandman.backend.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Read-only view onto chat_app.users — the shared identity table populated by user-service.
 */
@Entity
@Table(name = "users", schema = "chat_app")
@Data
@NoArgsConstructor
public class AppUser {

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
