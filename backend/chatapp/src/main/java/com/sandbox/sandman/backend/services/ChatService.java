package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.model.dto.ChatDto.ChatRequestDto;
import com.sandbox.sandman.backend.model.dto.ChatDto.MessageDto;
import com.sandbox.sandman.backend.model.dto.ChatDto.MessageHistoryResponse;
import com.sandbox.sandman.backend.model.entity.ChatEntity.AiContext;
import com.sandbox.sandman.backend.model.entity.ChatEntity.Message;
import com.sandbox.sandman.backend.model.entity.ChatEntity.Room;
import com.sandbox.sandman.backend.model.entity.ChatEntity.User;
import com.sandbox.sandman.backend.repositories.ChatRepository.AiContextRepository;
import com.sandbox.sandman.backend.repositories.ChatRepository.MessageRepository;
import com.sandbox.sandman.backend.repositories.ChatRepository.RoomRepository;
import com.sandbox.sandman.backend.repositories.ChatRepository.UserRepository;

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

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;

@Service
public class ChatService {

    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final AiContextRepository aiContextRepository;
    private final GroqAiClient groqAiClient;
    private final EmbeddingService embeddingService;

    public ChatService(MessageRepository messageRepository,
                       RoomRepository roomRepository,
                       UserRepository userRepository,
                       AiContextRepository aiContextRepository,
                       GroqAiClient groqAiClient,
                       EmbeddingService embeddingService) {
        this.messageRepository = messageRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.aiContextRepository = aiContextRepository;
        this.groqAiClient = groqAiClient;
        this.embeddingService = embeddingService;
    }

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int VECTOR_SEARCH_LIMIT = 5;

    @Cacheable(value = "chatHistory", key = "#roomId + '_' + #beforeId + '_' + #limit")
    public MessageHistoryResponse getChatHistoryByRoom(Long roomId, Long beforeId, int limit) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        int fetchSize = limit + 1;
        List<Message> raw;
        if (beforeId == null) {
            raw = messageRepository.findLatestByRoomId(room.getId(), PageRequest.of(0, fetchSize));
        } else {
            raw = messageRepository.findByRoomIdBeforeId(room.getId(), beforeId, PageRequest.of(0, fetchSize));
        }

        boolean hasMore = raw.size() > limit;
        List<Message> page = hasMore ? raw.subList(0, limit) : raw;
        Collections.reverse(page);

        // Get ai_name for this room (if it's an AI room)
        String aiName = aiContextRepository.findByRoomId(roomId)
                .map(AiContext::getAiName)
                .orElse("AI Assistant");

        List<MessageDto> dtos = page.stream()
                .map(msg -> convertToDto(msg, aiName))
                .collect(Collectors.toList());

        return new MessageHistoryResponse(dtos, hasMore);
    }

    @CacheEvict(value = "chatHistory", allEntries = true)
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

        // Save user's message
        Message userMessage = new Message();
        userMessage.setRoom(room);
        userMessage.setSender(user);
        userMessage.setIsAi(false);
        userMessage.setContent(request.getMessage());
        messageRepository.save(userMessage);

        // Embed user's message
        embeddingService.embedAndSave(userMessage);

        // Get AI context from room
        AiContext aiContext = aiContextRepository.findByRoomId(room.getId())
                .orElseThrow(() -> new RuntimeException("AI context not configured for this room"));

        return callAiAndSaveReply(room, aiContext, request.getMessage());
    }

    private String callAiAndSaveReply(Room room, AiContext aiContext, String userQuery) {
        List<org.springframework.ai.chat.messages.Message> aiPromptMessages = new ArrayList<>();

        // System prompt
        aiPromptMessages.add(new SystemMessage(aiContext.getSystemText()));

        // Vector search: find semantically similar past messages as extra context
        List<Long> similarIds = embeddingService.searchSimilarMessages(userQuery, room.getId(), VECTOR_SEARCH_LIMIT);
        if (!similarIds.isEmpty()) {
            List<Message> similarMessages = messageRepository.findAllById(similarIds);
            StringBuilder contextBuilder = new StringBuilder("Relevant past messages:\n");
            for (Message msg : similarMessages) {
                String senderLabel = Boolean.TRUE.equals(msg.getIsAi())
                        ? aiContext.getAiName()
                        : (msg.getSender() != null ? msg.getSender().getDisplayName() : "Unknown");
                contextBuilder.append("- ").append(senderLabel)
                        .append(": ").append(msg.getContent()).append("\n");
            }
            aiPromptMessages.add(new SystemMessage(contextBuilder.toString()));
        }

        // Recent chat history
        List<Message> history = messageRepository.findTopNByRoomId(room.getId(), PageRequest.of(0, DEFAULT_PAGE_SIZE));
        Collections.reverse(history);

        for (Message msg : history) {
            if (Boolean.TRUE.equals(msg.getIsAi())) {
                aiPromptMessages.add(new AssistantMessage(msg.getContent()));
            } else {
                aiPromptMessages.add(new UserMessage(msg.getContent()));
            }
        }

        // Call AI model
        Prompt prompt = new Prompt(aiPromptMessages);
        String aiReply = groqAiClient.chat(prompt);

        // Save AI reply (sender_id = NULL, is_ai = TRUE)
        Message aiMessageEntity = new Message();
        aiMessageEntity.setRoom(room);
        aiMessageEntity.setSender(null);
        aiMessageEntity.setIsAi(true);
        aiMessageEntity.setContent(aiReply);
        messageRepository.save(aiMessageEntity);

        // Embed AI reply
        embeddingService.embedAndSave(aiMessageEntity);

        return aiReply;
    }

    private MessageDto convertToDto(Message message, String aiName) {
        MessageDto dto = new MessageDto();
        dto.setId(message.getId());
        dto.setRoomId(message.getRoom().getId());
        dto.setIsAi(message.getIsAi());

        if (Boolean.TRUE.equals(message.getIsAi())) {
            dto.setSenderId(null);
            dto.setSenderName(aiName);
        } else {
            dto.setSenderId(message.getSender() != null ? message.getSender().getId() : null);
            dto.setSenderName(message.getSender() != null ? message.getSender().getDisplayName() : "Unknown");
        }

        dto.setContent(message.getContent());
        dto.setCreatedAt(message.getCreatedAt());
        return dto;
    }
}
