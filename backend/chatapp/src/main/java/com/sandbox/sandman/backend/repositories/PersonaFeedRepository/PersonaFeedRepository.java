package com.sandbox.sandman.backend.repositories.PersonaFeedRepository;

import com.sandbox.sandman.backend.model.dto.PersonaFeedDto.PersonaFeedPersonaProfileDto;
import com.sandbox.sandman.backend.model.dto.PersonaFeedDto.PersonaFeedPersonaSummaryDto;
import com.sandbox.sandman.backend.model.dto.PersonaFeedDto.PersonaFeedPostDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PersonaFeedRepository {

    private final JdbcTemplate jdbcTemplate;

    public List<PersonaFeedPostDto> findFeedPosts(Long userId, Long beforeId, int limit) {
        String beforeClause = beforeId == null ? "" : " AND p.id < ? ";
        String sql = """
                SELECT
                    p.id,
                    p.content,
                    p.image_urls,
                    p.created_at,
                    ac.id AS persona_id,
                    ac.ai_name,
                    ac.avatar_url,
                    ac.poster_url,
                    f.id IS NOT NULL AS followed_by_me
                FROM chat_app.persona_feed_posts p
                JOIN chat_app.ai_context ac ON ac.id = p.ai_context_id
                LEFT JOIN chat_app.ai_friends f ON f.ai_id = ac.id AND f.user_id = ?
                WHERE ac.visibility = 'public'
                  AND ac.persona_feed_enabled = true
                """ + beforeClause + """
                ORDER BY p.id DESC
                LIMIT ?
                """;

        if (beforeId == null) {
            return jdbcTemplate.query(sql, this::mapPost, userId, limit);
        }
        return jdbcTemplate.query(sql, this::mapPost, userId, beforeId, limit);
    }

    public List<PersonaFeedPostDto> findPostsByPersona(Long userId, Long personaId, Long beforeId, int limit) {
        String beforeClause = beforeId == null ? "" : " AND p.id < ? ";
        String sql = """
                SELECT
                    p.id,
                    p.content,
                    p.image_urls,
                    p.created_at,
                    ac.id AS persona_id,
                    ac.ai_name,
                    ac.avatar_url,
                    ac.poster_url,
                    f.id IS NOT NULL AS followed_by_me
                FROM chat_app.persona_feed_posts p
                JOIN chat_app.ai_context ac ON ac.id = p.ai_context_id
                LEFT JOIN chat_app.ai_friends f ON f.ai_id = ac.id AND f.user_id = ?
                WHERE ac.id = ?
                  AND ac.visibility = 'public'
                  AND ac.persona_feed_enabled = true
                """ + beforeClause + """
                ORDER BY p.id DESC
                LIMIT ?
                """;

        if (beforeId == null) {
            return jdbcTemplate.query(sql, this::mapPost, userId, personaId, limit);
        }
        return jdbcTemplate.query(sql, this::mapPost, userId, personaId, beforeId, limit);
    }

    public Optional<PersonaFeedPersonaProfileDto> findPersonaProfile(Long userId, Long personaId) {
        List<PersonaFeedPersonaProfileDto> rows = jdbcTemplate.query("""
                SELECT
                    ac.id,
                    ac.ai_name,
                    ac.avatar_url,
                    ac.poster_url,
                    ac.role,
                    ac.biography,
                    f.id IS NOT NULL AS followed_by_me
                FROM chat_app.ai_context ac
                LEFT JOIN chat_app.ai_friends f ON f.ai_id = ac.id AND f.user_id = ?
                WHERE ac.id = ?
                  AND ac.visibility = 'public'
                  AND ac.persona_feed_enabled = true
                LIMIT 1
                """, (rs, rowNum) -> new PersonaFeedPersonaProfileDto(
                rs.getLong("id"),
                rs.getString("ai_name"),
                rs.getString("avatar_url"),
                rs.getString("poster_url"),
                rs.getString("role"),
                rs.getString("biography"),
                rs.getBoolean("followed_by_me")
        ), userId, personaId);
        return rows.stream().findFirst();
    }

    public List<Long> claimDuePersonaIds(int limit) {
        return jdbcTemplate.query("""
                WITH due AS (
                    SELECT id
                    FROM chat_app.ai_context
                    WHERE visibility = 'public'
                      AND persona_feed_enabled = true
                      AND COALESCE(persona_feed_next_post_at, NOW()) <= NOW()
                    ORDER BY COALESCE(persona_feed_next_post_at, NOW()), id
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE chat_app.ai_context ac
                SET persona_feed_next_post_at = NOW() + INTERVAL '5 minutes'
                FROM due
                WHERE ac.id = due.id
                RETURNING ac.id
                """, (rs, rowNum) -> rs.getLong("id"), limit);
    }

    private PersonaFeedPostDto mapPost(ResultSet rs, int rowNum) throws SQLException {
        return new PersonaFeedPostDto(
                rs.getLong("id"),
                new PersonaFeedPersonaSummaryDto(
                        rs.getLong("persona_id"),
                        rs.getString("ai_name"),
                        rs.getString("avatar_url"),
                        rs.getString("poster_url")
                ),
                rs.getString("content"),
                toStringList(rs.getArray("image_urls")),
                rs.getObject("created_at", java.time.OffsetDateTime.class).toZonedDateTime(),
                rs.getBoolean("followed_by_me")
        );
    }

    private List<String> toStringList(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object value = array.getArray();
        if (value instanceof String[] strings) {
            return Arrays.asList(strings);
        }
        if (value instanceof Object[] objects) {
            return Arrays.stream(objects).map(String::valueOf).toList();
        }
        return List.of();
    }
}
