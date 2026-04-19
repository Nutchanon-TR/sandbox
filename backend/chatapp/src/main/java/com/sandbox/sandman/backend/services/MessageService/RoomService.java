package com.sandbox.sandman.backend.services.MessageService;

import com.sandbox.sandman.backend.model.dto.MessageDto.RoomCreateRequestDto;
import com.sandbox.sandman.backend.model.dto.MessageDto.RoomDto;
import com.sandbox.sandman.backend.model.entity.MessageEntity.AiContext;
import com.sandbox.sandman.backend.model.entity.MessageEntity.Room;
import com.sandbox.sandman.backend.model.entity.MessageEntity.User;
import com.sandbox.sandman.backend.repositories.MessageRepository.AiContextRepository;
import com.sandbox.sandman.backend.repositories.MessageRepository.RoomMemberRepository;
import com.sandbox.sandman.backend.repositories.MessageRepository.RoomRepository;
import com.sandbox.sandman.backend.repositories.MessageRepository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final AiContextRepository aiContextRepository;
    private final RoomMemberRepository roomMemberRepository;

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
        room.setUser(user);
        room.setName(request.getName());
        Room savedRoom = roomRepository.save(room);

        String aiName = request.getName() != null ? request.getName() : "AI Assistant";
        String systemPrompt = request.getSystemPrompt() != null
                ? request.getSystemPrompt()
                : "You are a helpful AI assistant. Respond concisely and helpfully.";

        AiContext aiContext = new AiContext();
        aiContext.setAiName(aiName);
        aiContext.setSystemText(systemPrompt);
        AiContext savedAi = aiContextRepository.save(aiContext);

        roomMemberRepository.addRoomAi(savedRoom.getId(), savedAi.getId());

        return toDto(savedRoom);
    }

    private RoomDto toDto(Room room) {
        String displayName = "AI Assistant";
        String aiAvatarUrl = null;
        Long aiId = roomMemberRepository.findAiIdByRoomId(room.getId());
        if (aiId != null) {
            AiContext aiContext = aiContextRepository.findById(aiId).orElse(null);
            if (aiContext != null) {
                displayName = aiContext.getAiName();
                aiAvatarUrl = aiContext.getAvatarUrl();
            }
        }

        return new RoomDto(
                room.getId(),
                displayName,
                aiAvatarUrl,
                room.getCreatedAt()
        );
    }
}
