package com.sandbox.sandman.backend.repositories.MessageRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RoomMemberRepository {

    private final JdbcTemplate jdbcTemplate;

    public void addRoomAi(Long roomId, Long aiId) {
        jdbcTemplate.update(
                "INSERT INTO chat_app.room_members (room_id, ai_id) VALUES (?, ?)",
                roomId, aiId);
    }

    public Long findAiIdByRoomId(Long roomId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT ai_id FROM chat_app.room_members WHERE room_id = ? LIMIT 1",
                    Long.class, roomId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public Long findLatestRoomIdByUserIdAndAiId(Long userId, Long aiId) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT r.id
                    FROM chat_app.rooms r
                    JOIN chat_app.room_members rm ON rm.room_id = r.id
                    WHERE r.user_id = ?
                      AND rm.ai_id = ?
                    ORDER BY r.created_at DESC
                    LIMIT 1
                    """, Long.class, userId, aiId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
}
