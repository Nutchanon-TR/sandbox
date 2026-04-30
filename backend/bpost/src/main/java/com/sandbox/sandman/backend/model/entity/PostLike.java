package com.sandbox.sandman.backend.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(name = "post_likes", schema = "b_post")
@IdClass(PostLike.PostLikeId.class)
@Data
@NoArgsConstructor
public class PostLike {

    @Id
    @Column(name = "post_id")
    private Long postId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = ZonedDateTime.now();
    }

    public static class PostLikeId implements Serializable {
        private Long postId;
        private Long userId;

        public PostLikeId() {}
        public PostLikeId(Long postId, Long userId) { this.postId = postId; this.userId = userId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PostLikeId that)) return false;
            return Objects.equals(postId, that.postId) && Objects.equals(userId, that.userId);
        }
        @Override public int hashCode() { return Objects.hash(postId, userId); }
    }
}
