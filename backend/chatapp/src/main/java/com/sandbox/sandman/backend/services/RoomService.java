package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.model.dto.ChatDto.RoomCreateRequestDto;
import com.sandbox.sandman.backend.model.dto.ChatDto.RoomDto;
import com.sandbox.sandman.backend.model.entity.ChatEntity.AiContext;
import com.sandbox.sandman.backend.model.entity.ChatEntity.Room;
import com.sandbox.sandman.backend.repositories.ChatRepository.AiContextRepository;
import com.sandbox.sandman.backend.repositories.ChatRepository.RoomRepository;
import com.sandbox.sandman.backend.repositories.ChatRepository.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final AiContextRepository aiContextRepository;
    private final JdbcTemplate jdbcTemplate;

    public RoomService(RoomRepository roomRepository,
                       UserRepository userRepository,
                       AiContextRepository aiContextRepository,
                       JdbcTemplate jdbcTemplate) {
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.aiContextRepository = aiContextRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RoomDto> listRoomsForUser(Long userId) {
        return roomRepository.findAllByUserId(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public RoomDto createRoom(Long userId, RoomCreateRequestDto request) {
        userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Room room = new Room();
        room.setIsGroup(request.getIsGroup());
        room.setCreatedBy(userId);

        if (Boolean.TRUE.equals(request.getIsGroup())) {
            room.setName(request.getName());  // group room ใช้ชื่อที่ตั้ง
        } else {
            room.setName(null);               // AI room ไม่ต้องมีชื่อ ใช้ ai_name แทน
            room.setAiModel(request.getAiModel());
        }
        Room savedRoom = roomRepository.save(room);

        // Add creator as member
        addRoomMember(savedRoom.getId(), userId);

        if (Boolean.TRUE.equals(request.getIsGroup())) {
            // Group room: add specified members
            if (request.getMemberIds() != null) {
                for (Long memberId : request.getMemberIds()) {
                    if (!memberId.equals(userId)) {
                        addRoomMember(savedRoom.getId(), memberId);
                    }
                }
            }
        } else {
            // AI room: create ai_context
            String aiName = request.getName() != null ? request.getName() : "AI Assistant";
            String systemPrompt = request.getSystemPrompt() != null
                    ? request.getSystemPrompt()
                    : "You are a helpful AI assistant. Respond concisely and helpfully.";

            AiContext aiContext = new AiContext();
            aiContext.setAiName(aiName);
            aiContext.setSystemText(systemPrompt);
            AiContext savedAi = aiContextRepository.save(aiContext);
            
            // Add AI as room member
            addRoomAi(savedRoom.getId(), savedAi.getId());
        }

        return toDto(savedRoom);
    }

    private void addRoomMember(Long roomId, Long userId) {
        jdbcTemplate.update(
                "INSERT INTO chat.room_members (room_id, user_id) VALUES (?, ?)",
                roomId, userId);
    }

    private void addRoomAi(Long roomId, Long aiId) {
        jdbcTemplate.update(
                "INSERT INTO chat.room_members (room_id, ai_id) VALUES (?, ?)",
                roomId, aiId);
    }

    private Long getAiIdForRoom(Long roomId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT ai_id FROM chat.room_members WHERE room_id = ? AND ai_id IS NOT NULL LIMIT 1",
                    Long.class, roomId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }

    private RoomDto toDto(Room room) {
        String displayName;
        String aiAvatarUrl = null;
        if (Boolean.TRUE.equals(room.getIsGroup())) {
            displayName = room.getName();
        } else {
            // AI room → ใช้ ai_name + avatar_url จาก ai_context
            Long aiId = getAiIdForRoom(room.getId());
            if (aiId != null) {
                AiContext aiContext = aiContextRepository.findById(aiId).orElse(null);
                displayName = aiContext != null ? aiContext.getAiName() : "AI Assistant";
                aiAvatarUrl = aiContext != null ? aiContext.getAvatarUrl() : null;
            } else {
                displayName = "AI Assistant";
            }
        }

        return new RoomDto(
                room.getId(),
                displayName,
                room.getIsGroup(),
                room.getAiModel(),
                aiAvatarUrl,
                room.getCreatedAt()
        );
    }
}
