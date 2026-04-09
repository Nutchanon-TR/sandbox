package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.model.dto.ChatDto.UserResolveResponseDto;
import com.sandbox.sandman.backend.model.entity.ChatEntity.Room;
import com.sandbox.sandman.backend.model.entity.ChatEntity.User;
import com.sandbox.sandman.backend.repositories.ChatRepository.RoomRepository;
import com.sandbox.sandman.backend.repositories.ChatRepository.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserResolutionService {

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final JdbcTemplate jdbcTemplate;

    public UserResolutionService(UserRepository userRepository,
                                  RoomRepository roomRepository,
                                  JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public UserResolveResponseDto resolveUser(String supabaseUidStr, String email, String username) {
        UUID supabaseUid = UUID.fromString(supabaseUidStr);

        // 1. Find or create the chat user
        User user = userRepository.findBySupabaseUid(supabaseUid)
                .orElseGet(() -> createUser(supabaseUid, email, username));

        // 2. Find or create the AI bot user
        User aiUser = userRepository.findByRole("AI")
                .orElseGet(() -> {
                    User ai = new User();
                    ai.setUsername("ai_assistant");
                    ai.setEmail("ai@sandbox.local");
                    ai.setPasswordHash("no-password");
                    ai.setRole("AI");
                    return userRepository.save(ai);
                });

        // 3. Find or create a private room between user and AI
        Room room = roomRepository.findPrivateRoomBetween(user.getId(), aiUser.getId())
                .orElseGet(() -> createRoomWithMembers(user, aiUser));

        return new UserResolveResponseDto(user.getId(), room.getId());
    }

    private User createUser(UUID supabaseUid, String email, String username) {
        User user = new User();
        user.setSupabaseUid(supabaseUid);
        user.setEmail(email);
        user.setUsername(username);
        user.setPasswordHash("supabase-auth");
        user.setRole("USER");
        return userRepository.save(user);
    }

    private Room createRoomWithMembers(User user, User aiUser) {
        Room room = new Room();
        room.setName(user.getUsername() + " & AI");
        room.setIsGroup(false);
        room.setCreatedBy(user.getId());
        room.setAiModel("llama3-8b-8192");
        Room savedRoom = roomRepository.save(room);

        // Insert room_members via JDBC (no JPA entity for junction table)
        jdbcTemplate.update(
                "INSERT INTO chat.room_members (room_id, user_id) VALUES (?, ?)",
                savedRoom.getId(), user.getId());
        jdbcTemplate.update(
                "INSERT INTO chat.room_members (room_id, user_id) VALUES (?, ?)",
                savedRoom.getId(), aiUser.getId());

        return savedRoom;
    }
}
