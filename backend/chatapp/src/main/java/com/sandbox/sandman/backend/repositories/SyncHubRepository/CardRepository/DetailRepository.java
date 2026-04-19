package com.sandbox.sandman.backend.repositories.SyncHubRepository.CardRepository;

import com.sandbox.sandman.backend.model.dto.SyncHubDto.AiDetailDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DetailRepository {

    private final JdbcTemplate jdbcTemplate;

    public Optional<AiDetailDto> findDetailById(Long aiId) {
        return jdbcTemplate.query(
                """
                SELECT
                    ac.id           AS ai_id,
                    ac.ai_name,
                    ac.avatar_url,
                    ac.poster_url,
                    ac.role,
                    ac."character"  AS character,
                    ac.biography,
                    ac.rule,
                    COALESCE(l.like_count, 0)       AS like_count,
                    COALESCE(f.friend_count, 0)     AS friend_count,
                    COALESCE(c.comment_count, 0)    AS comment_count
                FROM chat_app.ai_context ac
                LEFT JOIN (
                    SELECT ai_id, COUNT(*) AS like_count
                    FROM chat_app.ai_likes GROUP BY ai_id
                ) l ON l.ai_id = ac.id
                LEFT JOIN (
                    SELECT ai_id, COUNT(*) AS friend_count
                    FROM chat_app.ai_friends GROUP BY ai_id
                ) f ON f.ai_id = ac.id
                LEFT JOIN (
                    SELECT ai_id, COUNT(*) AS comment_count
                    FROM chat_app.comments GROUP BY ai_id
                ) c ON c.ai_id = ac.id
                WHERE ac.id = ?
                """,
                (rs, rowNum) -> new AiDetailDto(
                        rs.getLong("ai_id"),
                        rs.getString("ai_name"),
                        rs.getString("avatar_url"),
                        rs.getString("poster_url"),
                        rs.getString("role"),
                        rs.getString("character"),
                        rs.getString("biography"),
                        rs.getString("rule"),
                        rs.getLong("like_count"),
                        rs.getLong("friend_count"),
                        rs.getLong("comment_count")
                ),
                aiId
        ).stream().findFirst();
    }
}
