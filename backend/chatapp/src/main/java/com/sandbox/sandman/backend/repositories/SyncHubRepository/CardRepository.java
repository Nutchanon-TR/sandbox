package com.sandbox.sandman.backend.repositories.SyncHubRepository;

import com.sandbox.sandman.backend.model.dto.SyncHubDto.AiContextUserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class CardRepository {

    private final JdbcTemplate jdbcTemplate;

    public List<AiContextUserDto> findAiContextsWithUsers() {
        return jdbcTemplate.query(
                """
                SELECT
                    ac.id          AS ai_id,
                    ac.ai_name,
                    ac.avatar_url,
                    u.id           AS user_id,
                    u.display_name,
                    r.id           AS room_id
                FROM chat_app.ai_context ac
                JOIN chat_app.room_members rm ON rm.ai_id = ac.id
                JOIN chat_app.rooms r ON r.id = rm.room_id AND r.is_group = false
                JOIN chat_app.users u ON u.id = r.user_id
                ORDER BY ac.id, u.id
                """,
                (rs, rowNum) -> new AiContextUserDto(
                        rs.getLong("ai_id"),
                        rs.getString("ai_name"),
                        rs.getString("avatar_url"),
                        rs.getLong("user_id"),
                        rs.getString("display_name"),
                        rs.getLong("room_id")
                )
        );
    }
}
