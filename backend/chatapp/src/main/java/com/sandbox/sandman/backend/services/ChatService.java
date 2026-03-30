package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.model.dto.ChatDto.ChatRequestDto;
import com.sandbox.sandman.backend.model.dto.ChatDto.MessageDto;
import com.sandbox.sandman.backend.model.entity.ChatEntity.AiContext;
import com.sandbox.sandman.backend.model.entity.ChatEntity.Message;
import com.sandbox.sandman.backend.model.entity.ChatEntity.Room;
import com.sandbox.sandman.backend.model.entity.ChatEntity.User;
import com.sandbox.sandman.backend.repositories.ChatRepository.AiContextRepository;
import com.sandbox.sandman.backend.repositories.ChatRepository.MessageRepository;
import com.sandbox.sandman.backend.repositories.ChatRepository.RoomRepository;
import com.sandbox.sandman.backend.repositories.ChatRepository.UserRepository;

// Spring AI Imports
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;

@Service
public class ChatService {

    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final AiContextRepository aiContextRepository;

    private final ChatClient chatClient;

    public ChatService(MessageRepository messageRepository,
                       RoomRepository roomRepository,
                       UserRepository userRepository,
                       AiContextRepository aiContextRepository,
                       ChatClient.Builder chatClientBuilder) {
        this.messageRepository = messageRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.aiContextRepository = aiContextRepository;
        this.chatClient = chatClientBuilder.build();
    }

    // [Phase 2 Prototype]: Cache this method to avoid DB hits
    @Cacheable(value = "chatHistory", key = "#roomId")
    public List<MessageDto> getChatHistoryByRoom(Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        return messageRepository.findByRoomIdOrderByCreatedAtAsc(room.getId())
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    // [Phase 2 Prototype]: Evict cache when new message arrives
    @CacheEvict(value = "chatHistory", key = "#request.roomId")
    public String getAiResponse(ChatRequestDto request) {
        // STEP 1: Validate and find room
        Long reqRoomId = request.getRoomId();
        if (reqRoomId == null) {
            throw new RuntimeException("Room ID is required");
        }
        Room room = roomRepository.findById(reqRoomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        // STEP 2: Validate and find sender
        Long reqSenderId = request.getSenderId();
        if (reqSenderId == null) {
            throw new RuntimeException("Sender ID is required");
        }
        User user = userRepository.findById(reqSenderId)
                .orElseThrow(() -> new RuntimeException("Sender not found"));

        // STEP 3: Save user's message to DB
        Message userMessage = new Message();
        userMessage.setRoom(room);
        userMessage.setSender(user);
        userMessage.setContent(request.getMessage());
        messageRepository.save(userMessage);

        // STEP 4: Get AI context (system prompt) from DB
        User aiUser = getOrCreateAiChatBot();
        AiContext aiContext = aiContextRepository.findByUserId(aiUser.getId())
                .orElseThrow(() -> new RuntimeException("AI context not configured for this chatbot"));
        String systemText = aiContext.getSystemText();

        // STEP 5: Call AI and save reply
        return callAiAndSaveReply(room, aiUser, systemText);
    }

    /**
     * Builds the AI prompt from chat history and system context,
     * sends it to the AI model, saves the reply, and returns it.
     */
    private String callAiAndSaveReply(Room room, User aiUser, String systemText) {
        // Build prompt messages
        org.springframework.ai.chat.messages.Message systemMessage = new SystemMessage(systemText);

        List<org.springframework.ai.chat.messages.Message> aiPromptMessages = new ArrayList<>();
        aiPromptMessages.add(systemMessage);

        List<Message> history = messageRepository.findByRoomIdOrderByCreatedAtAsc(room.getId());

        for (Message msg : history) {
            if (msg.getSender().getId().equals(aiUser.getId())) {
                aiPromptMessages.add(new AssistantMessage(msg.getContent()));
            } else {
                aiPromptMessages.add(new UserMessage(msg.getContent()));
            }
        }

        // Call AI model
        Prompt prompt = new Prompt(aiPromptMessages);
        String aiReply = chatClient.prompt(prompt).call().content();

        // Save AI reply to DB
        Message aiMessageEntity = new Message();
        aiMessageEntity.setRoom(room);
        aiMessageEntity.setSender(aiUser);
        aiMessageEntity.setContent(aiReply);
        messageRepository.save(aiMessageEntity);

        return aiReply;
    }

    private MessageDto convertToDto(Message message) {
        MessageDto dto = new MessageDto();
        dto.setId(message.getId());
        dto.setRoomId(message.getRoom().getId());
        dto.setSenderId(message.getSender().getId());
        dto.setSenderUsername(message.getSender().getUsername());
        dto.setSenderRole(message.getSender().getRole());
        dto.setContent(message.getContent());
        dto.setCreatedAt(message.getCreatedAt());
        return dto;
    }

    private User getOrCreateAiChatBot() {
        return userRepository.findByRole("AI")
                .orElseGet(() -> {
                    User ai = new User();
                    ai.setUsername("ai_assistant");
                    ai.setEmail("ai@sandbox.local");
                    ai.setPasswordHash("no-password");
                    ai.setRole("AI");
                    return userRepository.save(ai);
                });
    }
}