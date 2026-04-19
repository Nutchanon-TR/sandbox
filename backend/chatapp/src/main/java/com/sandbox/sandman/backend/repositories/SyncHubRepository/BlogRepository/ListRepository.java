package com.sandbox.sandman.backend.repositories.SyncHubRepository.BlogRepository;

import com.sandbox.sandman.backend.model.dto.SyncHubDto.AiContextUserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ListRepository {

    private final JdbcTemplate jdbcTemplate;

    public List<AiContextUserDto> findAiContextsWithUsers() {
        return jdbcTemplate.query(
                """
                SELECT
                    ac.id          AS ai_id,
                    ac.ai_name,
                    ac.avatar_url,
                    ac.poster_url,
                    u.id           AS user_id,
                    u.display_name,
                    r.id           AS room_id,
                    COALESCE(l.like_count, 0)    AS like_count,
                    COALESCE(f.friend_count, 0)  AS friend_count,
                    COALESCE(c.comment_count, 0) AS comment_count
                FROM chat_app.ai_context ac
                JOIN chat_app.room_members rm ON rm.ai_id = ac.id
                JOIN chat_app.rooms r ON r.id = rm.room_id AND r.is_group = false
                JOIN chat_app.users u ON u.id = r.user_id
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
                ORDER BY ac.id, u.id
                """,
                (rs, rowNum) -> new AiContextUserDto(
                        rs.getLong("ai_id"),
                        rs.getString("ai_name"),
                        rs.getString("avatar_url"),
                        rs.getString("poster_url"),
                        rs.getLong("user_id"),
                        rs.getString("display_name"),
                        rs.getLong("room_id"),
                        rs.getLong("like_count"),
                        rs.getLong("friend_count"),
                        rs.getLong("comment_count")
                )
        );
    }
}
