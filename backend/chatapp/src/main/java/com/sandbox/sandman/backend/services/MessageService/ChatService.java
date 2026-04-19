package com.sandbox.sandman.backend.services.MessageService;

import com.sandbox.sandman.backend.model.dto.MessageDto.ChatDto;
import com.sandbox.sandman.backend.model.dto.MessageDto.ChatHistoryResponse;
import com.sandbox.sandman.backend.model.dto.MessageDto.ChatRequestDto;
import com.sandbox.sandman.backend.model.entity.MessageEntity.AiContext;
import com.sandbox.sandman.backend.model.entity.MessageEntity.Chat;
import com.sandbox.sandman.backend.model.entity.MessageEntity.Room;
import com.sandbox.sandman.backend.model.entity.MessageEntity.User;
import com.sandbox.sandman.backend.repositories.MessageRepository.AiContextRepository;
import com.sandbox.sandman.backend.repositories.MessageRepository.ChatRepository;
import com.sandbox.sandman.backend.repositories.MessageRepository.RoomMemberRepository;
import com.sandbox.sandman.backend.repositories.MessageRepository.RoomRepository;
import com.sandbox.sandman.backend.repositories.MessageRepository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRepository chatRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final AiContextRepository aiContextRepository;
    private final GroqAiClient groqAiClient;
    private final EmbeddingService embeddingService;
    private final RoomMemberRepository roomMemberRepository;

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int VECTOR_SEARCH_LIMIT = 5;

    public ChatHistoryResponse getChatHistoryByRoom(Long roomId, Long beforeId, int limit) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        int fetchSize = limit + 1;
        List<Chat> raw;
        if (beforeId == null) {
            raw = chatRepository.findLatestByRoomId(room.getId(), PageRequest.of(0, fetchSize));
        } else {
            raw = chatRepository.findByRoomIdBeforeId(room.getId(), beforeId, PageRequest.of(0, fetchSize));
        }

        boolean hasMore = raw.size() > limit;
        List<Chat> page = hasMore ? raw.subList(0, limit) : raw;
        Collections.reverse(page);

        Long aiId = roomMemberRepository.findAiIdByRoomId(roomId);
        final String aiName = (aiId != null)
                ? aiContextRepository.findById(aiId).map(AiContext::getAiName).orElse("AI Assistant")
                : "AI Assistant";

        List<ChatDto> dtos = page.stream()
                .map(msg -> convertToDto(msg, aiName))
                .collect(Collectors.toList());

        return new ChatHistoryResponse(dtos, hasMore);
    }

    public String getAiResponse(ChatRequestDto request) {
        Long reqRoomId = request.getRoomId();
        if (reqRoomId == null) {
            throw new RuntimeException("Room ID is required");
        }
        Room room = roomRepository.findById(reqRoomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        Long reqSenderId = request.getSenderId();
        if (reqSenderId == null) {
            throw new RuntimeException("Sender ID is required");
        }
        User user = userRepository.findById(reqSenderId)
                .orElseThrow(() -> new RuntimeException("Sender not found"));

        Chat userChat = new Chat();
        userChat.setRoom(room);
        userChat.setSender(user);
        userChat.setIsAi(false);
        userChat.setContent(request.getMessage());
        chatRepository.save(userChat);

        embeddingService.embedAndSave(userChat);

        Long aiId = roomMemberRepository.findAiIdByRoomId(room.getId());
        if (aiId == null) {
            throw new RuntimeException("AI context not configured for this room");
        }
        AiContext aiContext = aiContextRepository.findById(aiId)
                .orElseThrow(() -> new RuntimeException("AI context not configured for this room"));

        return callAiAndSaveReply(room, aiContext, request.getMessage());
    }

    private String callAiAndSaveReply(Room room, AiContext aiContext, String userQuery) {
        List<org.springframework.ai.chat.messages.Message> aiPromptMessages = new ArrayList<>();

        aiPromptMessages.add(new SystemMessage(aiContext.buildSystemPrompt()));

        List<Long> similarIds = embeddingService.searchSimilarMessages(userQuery, room.getId(), VECTOR_SEARCH_LIMIT);
        if (!similarIds.isEmpty()) {
            List<Chat> similarChats = chatRepository.findAllById(similarIds);
            StringBuilder contextBuilder = new StringBuilder("Relevant past messages:\n");
            for (Chat msg : similarChats) {
                String senderLabel = Boolean.TRUE.equals(msg.getIsAi())
                        ? aiContext.getAiName()
                        : (msg.getSender() != null ? msg.getSender().getDisplayName() : "Unknown");
                contextBuilder.append("- ").append(senderLabel)
                        .append(": ").append(msg.getContent()).append("\n");
            }
            aiPromptMessages.add(new SystemMessage(contextBuilder.toString()));
        }

        List<Chat> history = chatRepository.findTopNByRoomId(room.getId(), PageRequest.of(0, DEFAULT_PAGE_SIZE));
        Collections.reverse(history);

        for (Chat msg : history) {
            if (Boolean.TRUE.equals(msg.getIsAi())) {
                aiPromptMessages.add(new AssistantMessage(msg.getContent()));
            } else {
                aiPromptMessages.add(new UserMessage(msg.getContent()));
            }
        }

        Prompt prompt = new Prompt(aiPromptMessages);
        String aiReply = groqAiClient.chat(prompt);

        Chat aiChatEntity = new Chat();
        aiChatEntity.setRoom(room);
        aiChatEntity.setSender(null);
        aiChatEntity.setIsAi(true);
        aiChatEntity.setContent(aiReply);
        chatRepository.save(aiChatEntity);

        embeddingService.embedAndSave(aiChatEntity);

        return aiReply;
    }

    private ChatDto convertToDto(Chat chat, String aiName) {
        ChatDto dto = new ChatDto();
        dto.setId(chat.getId());
        dto.setRoomId(chat.getRoom().getId());
        dto.setIsAi(chat.getIsAi());

        if (Boolean.TRUE.equals(chat.getIsAi())) {
            dto.setSenderId(null);
            dto.setSenderName(aiName);
        } else {
            dto.setSenderId(chat.getSender() != null ? chat.getSender().getId() : null);
            dto.setSenderName(chat.getSender() != null ? chat.getSender().getDisplayName() : "Unknown");
        }

        dto.setContent(chat.getContent());
        dto.setCreatedAt(chat.getCreatedAt());
        return dto;
    }
}
