package com.sandbox.sandman.backend.services.MessageService;

import com.sandbox.sandman.backend.model.dto.MessageDto.RoomCreateRequestDto;
import com.sandbox.sandman.backend.model.dto.MessageDto.RoomDto;
import com.sandbox.sandman.backend.model.entity.MessageEntity.AiContext;
import com.sandbox.sandman.backend.model.entity.MessageEntity.Room;
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
        userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Room room = new Room();
        room.setIsGroup(request.getIsGroup());
        room.setCreatedBy(userId);

        if (Boolean.TRUE.equals(request.getIsGroup())) {
            room.setName(request.getName());
        } else {
            room.setName(null);
            room.setAiModel(request.getAiModel());
        }
        Room savedRoom = roomRepository.save(room);

        roomMemberRepository.addRoomMember(savedRoom.getId(), userId);

        if (Boolean.TRUE.equals(request.getIsGroup())) {
            if (request.getMemberIds() != null) {
                for (Long memberId : request.getMemberIds()) {
                    if (!memberId.equals(userId)) {
                        roomMemberRepository.addRoomMember(savedRoom.getId(), memberId);
                    }
                }
            }
        } else {
            String aiName = request.getName() != null ? request.getName() : "AI Assistant";
            String systemPrompt = request.getSystemPrompt() != null
                    ? request.getSystemPrompt()
                    : "You are a helpful AI assistant. Respond concisely and helpfully.";

            AiContext aiContext = new AiContext();
            aiContext.setAiName(aiName);
            aiContext.setSystemText(systemPrompt);
            AiContext savedAi = aiContextRepository.save(aiContext);

            roomMemberRepository.addRoomAi(savedRoom.getId(), savedAi.getId());
        }

        return toDto(savedRoom);
    }

    private RoomDto toDto(Room room) {
        String displayName;
        String aiAvatarUrl = null;
        if (Boolean.TRUE.equals(room.getIsGroup())) {
            displayName = room.getName();
        } else {
            Long aiId = roomMemberRepository.findAiIdByRoomId(room.getId());
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
