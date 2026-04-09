package com.sandbox.sandman.backend.model.entity.UserEntity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "profiles", schema = "users")
@Data
@NoArgsConstructor
public class Profile {

    @Id
    @Column(name = "supabase_uid")
    private UUID supabaseUid;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 20)
    private String role = "USER";

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
    }
}
