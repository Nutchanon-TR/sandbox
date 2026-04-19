package com.sandbox.sandman.backend.repositories.MessageRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RoomMemberRepository {

    private final JdbcTemplate jdbcTemplate;

    public void addRoomMember(Long roomId, Long userId) {
        jdbcTemplate.update(
                "INSERT INTO chat_app.room_members (room_id, user_id) VALUES (?, ?)",
                roomId, userId);
    }

    public void addRoomAi(Long roomId, Long aiId) {
        jdbcTemplate.update(
                "INSERT INTO chat_app.room_members (room_id, ai_id) VALUES (?, ?)",
                roomId, aiId);
    }

    public Long findAiIdByRoomId(Long roomId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT ai_id FROM chat_app.room_members WHERE room_id = ? AND ai_id IS NOT NULL LIMIT 1",
                    Long.class, roomId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
}
