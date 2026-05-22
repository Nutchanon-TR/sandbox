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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
    public RoomDto openRoomForPersona(Long userId, AiContext persona) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Long existingRoomId = roomMemberRepository.findLatestRoomIdByUserIdAndAiId(user.getId(), persona.getId());
        if (existingRoomId != null) {
            return roomRepository.findById(existingRoomId)
                    .map(this::toDto)
                    .orElseGet(() -> createRoomForAi(user, new RoomCreateRequestDto(), persona));
        }

        RoomCreateRequestDto request = new RoomCreateRequestDto();
        request.setName(persona.getAiName());
        request.setIsGroup(false);
        request.setAiContextId(persona.getId());
        return createRoomForAi(user, request, persona);
    }

    @Transactional
    public RoomDto createRoom(Long userId, RoomCreateRequestDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        AiContext roomAi = resolveRoomAi(user, request);
        if (request.getAiContextId() != null) {
            Long existingRoomId = roomMemberRepository.findLatestRoomIdByUserIdAndAiId(user.getId(), roomAi.getId());
            if (existingRoomId == null) {
                return createRoomForAi(user, request, roomAi);
            }
            return roomRepository.findById(existingRoomId)
                    .map(this::toDto)
                    .orElseGet(() -> createRoomForAi(user, request, roomAi));
        }

        return createRoomForAi(user, request, roomAi);
    }

    private RoomDto createRoomForAi(User user, RoomCreateRequestDto request, AiContext roomAi) {
        String roomName = request.getName() != null && !request.getName().isBlank()
                ? request.getName().trim()
                : roomAi.getAiName();

        Room room = new Room();
        room.setUser(user);
        room.setIsGroup(Boolean.TRUE.equals(request.getIsGroup()));
        room.setName(roomName);
        Room savedRoom = roomRepository.save(room);

        roomMemberRepository.addRoomAi(savedRoom.getId(), roomAi.getId());

        return toDto(savedRoom);
    }

    private AiContext resolveRoomAi(User user, RoomCreateRequestDto request) {
        if (request.getAiContextId() != null) {
            return aiContextRepository.findActiveByIdAndOwner(request.getAiContextId(), user.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found"));
        }

        String aiName = request.getName() != null ? request.getName() : "AI Assistant";
        String systemPrompt = request.getSystemPrompt() != null
                ? request.getSystemPrompt()
                : "You are a helpful AI assistant. Respond concisely and helpfully.";

        AiContext aiContext = new AiContext();
        aiContext.setCreatedByUser(user);
        aiContext.setAiName(aiName);
        aiContext.setRole(systemPrompt);
        return aiContextRepository.save(aiContext);
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
                room.getIsGroup(),
                aiId,
                aiAvatarUrl,
                room.getCreatedAt()
        );
    }
}
