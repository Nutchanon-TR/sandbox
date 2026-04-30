package com.sandbox.sandman.backend.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "notifications", schema = "b_post")
@Data
@NoArgsConstructor
public class Notification {

    public static final String TYPE_POST_LIKE = "POST_LIKE";
    public static final String TYPE_POST_COMMENT = "POST_COMMENT";
    public static final String TYPE_FRIEND_REQUEST = "FRIEND_REQUEST";
    public static final String TYPE_FRIEND_ACCEPT = "FRIEND_ACCEPT";
    public static final String TYPE_MESSAGE = "MESSAGE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "target_kind", length = 32)
    private String targetKind;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "read_at")
    private ZonedDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = ZonedDateTime.now();
    }
}
