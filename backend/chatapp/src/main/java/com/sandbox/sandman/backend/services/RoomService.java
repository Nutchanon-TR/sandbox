package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.model.dto.ChatDto.RoomCreateRequestDto;
import com.sandbox.sandman.backend.model.dto.ChatDto.RoomDto;
import com.sandbox.sandman.backend.model.entity.ChatEntity.AiContext;
import com.sandbox.sandman.backend.model.entity.ChatEntity.Room;
import com.sandbox.sandman.backend.model.entity.ChatEntity.User;
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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Room room = new Room();
        room.setName(request.getName());
        room.setIsGroup(request.getIsGroup());
        room.setCreatedBy(userId);
        room.setAiModel(request.getAiModel());
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
            // AI room: add AI bot as member
            User aiUser = userRepository.findByRole("AI")
                    .orElseGet(() -> {
                        User ai = new User();
                        ai.setUsername("ai_assistant");
                        ai.setEmail("ai@sandbox.local");
                        ai.setPasswordHash("no-password");
                        ai.setRole("AI");
                        return userRepository.save(ai);
                    });
            addRoomMember(savedRoom.getId(), aiUser.getId());

            // Create AI context (system prompt) for this room
            String systemPrompt = request.getSystemPrompt() != null
                    ? request.getSystemPrompt()
                    : "You are a helpful AI assistant. Respond concisely and helpfully.";
            AiContext aiContext = new AiContext();
            aiContext.setRoom(savedRoom);
            aiContext.setSystemText(systemPrompt);
            aiContextRepository.save(aiContext);
        }

        return toDto(savedRoom);
    }

    private void addRoomMember(Long roomId, Long userId) {
        jdbcTemplate.update(
                "INSERT INTO chat.room_members (room_id, user_id) VALUES (?, ?)",
                roomId, userId);
    }

    private RoomDto toDto(Room room) {
        return new RoomDto(
                room.getId(),
                room.getName(),
                room.getIsGroup(),
                room.getAiModel(),
                room.getCreatedAt()
        );
    }
}
