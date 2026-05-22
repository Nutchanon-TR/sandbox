package com.sandbox.sandman.backend.services.MessageService;

import com.sandbox.sandman.backend.model.dto.MessageDto.ChatDto;
import com.sandbox.sandman.backend.model.dto.MessageDto.ChatHistoryResponse;
import com.sandbox.sandman.backend.model.dto.MessageDto.ChatRequestDto;
import com.sandbox.sandman.backend.model.dto.MessageDto.ChatResponseDto;
import com.sandbox.sandman.backend.model.dto.MessageDto.ChatAttachmentDto;
import com.sandbox.sandman.backend.model.entity.MessageEntity.AiContext;
import com.sandbox.sandman.backend.model.entity.MessageEntity.Chat;
import com.sandbox.sandman.backend.model.entity.MessageEntity.ChatAttachment;
import com.sandbox.sandman.backend.model.entity.MessageEntity.Room;
import com.sandbox.sandman.backend.model.entity.MessageEntity.User;
import com.sandbox.sandman.backend.repositories.MessageRepository.AiContextRepository;
import com.sandbox.sandman.backend.repositories.MessageRepository.ChatAttachmentRepository;
import com.sandbox.sandman.backend.repositories.MessageRepository.ChatRepository;
import com.sandbox.sandman.backend.repositories.MessageRepository.RoomMemberRepository;
import com.sandbox.sandman.backend.repositories.MessageRepository.RoomRepository;
import com.sandbox.sandman.backend.repositories.MessageRepository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private static final List<String> SNAPSHOT_ACTIVITIES = List.of(
            "reading a book",
            "drinking coffee",
            "walking outside",
            "studying at a desk",
            "cooking",
            "sketching",
            "looking at a laptop",
            "sitting by a window"
    );

    private final ChatRepository chatRepository;
    private final ChatAttachmentRepository chatAttachmentRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final AiContextRepository aiContextRepository;
    private final GroqAiClient groqAiClient;
    private final EmbeddingService embeddingService;
    private final RoomMemberRepository roomMemberRepository;
    private final ImageTriggerService imageTriggerService;
    private final CloudflareImageService cloudflareImageService;
    private final SupabaseStorageService supabaseStorageService;

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int VECTOR_SEARCH_LIMIT = 5;

    @Transactional(readOnly = true)
    public ChatHistoryResponse getChatHistoryByRoom(Long callerId, Long roomId, Long beforeId, int limit) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
        verifyRoomOwner(room, callerId);

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
        Map<Long, List<ChatAttachmentDto>> attachmentMap = loadAttachmentMap(page);

        Long aiId = roomMemberRepository.findAiIdByRoomId(roomId);
        final String aiName = (aiId != null)
                ? aiContextRepository.findById(aiId).map(AiContext::getAiName).orElse("AI Assistant")
                : "AI Assistant";

        List<ChatDto> dtos = page.stream()
                .map(msg -> convertToDto(msg, aiName, attachmentMap.getOrDefault(msg.getId(), List.of())))
                .collect(Collectors.toList());

        return new ChatHistoryResponse(dtos, hasMore);
    }

    @Transactional
    public ChatResponseDto getAiResponse(Long callerId, ChatRequestDto request) {
        Long reqRoomId = request.getRoomId();
        if (reqRoomId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room ID is required");
        }
        Room room = roomRepository.findById(reqRoomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
        verifyRoomOwner(room, callerId);

        // callerId is server-derived from JWT (CurrentUser); never trust request body for identity.
        User user = userRepository.findById(callerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sender not found"));

        Chat userChat = new Chat();
        userChat.setRoom(room);
        userChat.setSender(user);
        userChat.setIsAi(false);
        userChat.setContent(request.getMessage());
        chatRepository.save(userChat);

        embeddingService.embedAndSave(userChat);

        Long aiId = roomMemberRepository.findAiIdByRoomId(room.getId());
        if (aiId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AI context not configured for this room");
        }
        AiContext aiContext = aiContextRepository.findById(aiId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "AI context not configured for this room"));

        ImageTriggerService.ImageTriggerDecision imageDecision = imageTriggerService.detect(request.getMessage(), aiContext);
        if (imageDecision.shouldGenerateImage()) {
            return generateImageReply(room, aiContext, request.getMessage(), imageDecision);
        }

        return callAiAndSaveReply(room, aiContext, request.getMessage());
    }

    private ChatResponseDto callAiAndSaveReply(Room room, AiContext aiContext, String userQuery) {
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

        Chat aiChatEntity = saveAiChat(room, aiReply);

        embeddingService.embedAndSave(aiChatEntity);

        return new ChatResponseDto(aiReply, List.of());
    }

    private ChatResponseDto generateImageReply(
            Room room,
            AiContext aiContext,
            String userQuery,
            ImageTriggerService.ImageTriggerDecision imageDecision) {
        String prompt = buildImagePrompt(aiContext, userQuery, imageDecision.type());
        String fallbackSuccessReply = imageDecision.type() == ImageTriggerService.ImageTriggerType.FIRST_PERSON_SNAPSHOT
                ? "ถ่ายมาให้ดูแล้วนะ"
                : "สร้างรูปให้แล้วนะ";

        try {
            CloudflareImageService.GeneratedImage image = cloudflareImageService.generate(prompt);
            SupabaseStorageService.UploadResult upload = supabaseStorageService.uploadGeneratedImage(image.bytes(), image.mimeType());
            String successReply = generateImageAwareReply(
                    aiContext,
                    userQuery,
                    prompt,
                    imageDecision.type(),
                    fallbackSuccessReply
            );
            Chat aiChat = saveAiChat(room, successReply);
            embeddingService.embedAndSave(aiChat);

            ChatAttachment attachment = new ChatAttachment();
            attachment.setChat(aiChat);
            attachment.setType("image");
            attachment.setUrl(upload.publicUrl());
            attachment.setMimeType(image.mimeType());
            attachment.setPrompt(prompt);
            attachment.setProvider("cloudflare-workers-ai");
            attachment.setMetadata("""
                    {"triggerType":"%s","objectPath":"%s","model":"%s"}
                    """.formatted(imageDecision.type().name(), upload.objectPath(), image.model()).trim());
            ChatAttachment savedAttachment = chatAttachmentRepository.save(attachment);

            return new ChatResponseDto(successReply, List.of(convertAttachmentToDto(savedAttachment)));
        } catch (Exception e) {
            log.warn("Image generation failed for room {} and trigger {}", room.getId(), imageDecision.type(), e);
            String fallbackReply = "ตอนนี้ยังสร้างรูปไม่ได้ แต่คุยต่อได้ปกตินะ";
            Chat aiChat = saveAiChat(room, fallbackReply);
            embeddingService.embedAndSave(aiChat);
            return new ChatResponseDto(fallbackReply, List.of());
        }
    }

    private String generateImageAwareReply(
            AiContext aiContext,
            String userQuery,
            String imagePrompt,
            ImageTriggerService.ImageTriggerType triggerType,
            String fallbackReply) {
        try {
            List<org.springframework.ai.chat.messages.Message> replyPrompt = new ArrayList<>();
            replyPrompt.add(new SystemMessage(aiContext.buildSystemPrompt()));
            replyPrompt.add(new SystemMessage("""
                    You are sending the user a generated image attachment with this reply.
                    Write the chat reply in character and answer the user's request naturally.
                    Use the generated image description as the source of truth for what the image shows.
                    Keep the reply consistent with the image, the user's question, and the character.
                    Do not mention hidden prompts, providers, model names, or that you cannot inspect the attachment.
                    Keep the reply concise unless the user's question needs a longer answer.
                    """));
            replyPrompt.add(new UserMessage("""
                    User message:
                    %s

                    Image trigger:
                    %s

                    Generated image description:
                    %s
                    """.formatted(safe(userQuery), triggerType.name(), safe(imagePrompt))));

            String reply = groqAiClient.chat(new Prompt(replyPrompt));
            return reply == null || reply.isBlank() ? fallbackReply : reply.trim();
        } catch (Exception e) {
            log.warn("Image reply text generation failed for trigger {}", triggerType, e);
            return fallbackReply;
        }
    }

    private Chat saveAiChat(Room room, String content) {
        Chat aiChatEntity = new Chat();
        aiChatEntity.setRoom(room);
        aiChatEntity.setSender(null);
        aiChatEntity.setIsAi(true);
        aiChatEntity.setContent(content);
        return chatRepository.save(aiChatEntity);
    }

    private String buildImagePrompt(
            AiContext aiContext,
            String userQuery,
            ImageTriggerService.ImageTriggerType triggerType) {
        String activity = SNAPSHOT_ACTIVITIES.get(ThreadLocalRandom.current().nextInt(SNAPSHOT_ACTIVITIES.size()));
        String characterDescription = buildCharacterDescription(aiContext);

        String template = aiContext.getImagePromptTemplate();
        if (template == null || template.isBlank()) {
            if (triggerType == ImageTriggerService.ImageTriggerType.FIRST_PERSON_SNAPSHOT) {
                template = """
                        First-person smartphone photo from the perspective of {ai_name}.
                        They are currently {random_activity}.
                        Style and personality: {character_biography}.
                        Natural candid photo, realistic, casual, no text, no watermark.
                        """;
            } else {
                template = """
                        Create a realistic image requested by the user: {user_message}.
                        The image should fit the style and personality of {ai_name}: {character_biography}.
                        Natural, polished, no text, no watermark.
                        """;
            }
        }

        return template
                .replace("{ai_name}", safe(aiContext.getAiName()))
                .replace("{random_activity}", activity)
                .replace("{character}", safe(aiContext.getCharacter()))
                .replace("{biography}", safe(aiContext.getBiography()))
                .replace("{character_biography}", characterDescription)
                .replace("{user_message}", safe(userQuery))
                .trim();
    }

    private String buildCharacterDescription(AiContext aiContext) {
        StringBuilder sb = new StringBuilder();
        appendInline(sb, aiContext.getCharacter());
        appendInline(sb, aiContext.getBiography());
        appendInline(sb, aiContext.getRole());
        return sb.length() == 0 ? "friendly AI character" : sb.toString();
    }

    private void appendInline(StringBuilder sb, String value) {
        if (value == null || value.isBlank()) return;
        if (sb.length() > 0) sb.append(" ");
        sb.append(value.trim());
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private Map<Long, List<ChatAttachmentDto>> loadAttachmentMap(List<Chat> chats) {
        List<Long> chatIds = chats.stream()
                .map(Chat::getId)
                .toList();
        if (chatIds.isEmpty()) {
            return Map.of();
        }
        return chatAttachmentRepository.findByChatIdIn(chatIds).stream()
                .collect(Collectors.groupingBy(
                        attachment -> attachment.getChat().getId(),
                        Collectors.mapping(this::convertAttachmentToDto, Collectors.toList())
                ));
    }

    private ChatDto convertToDto(Chat chat, String aiName, List<ChatAttachmentDto> attachments) {
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
        dto.setAttachments(attachments);
        return dto;
    }

    private ChatAttachmentDto convertAttachmentToDto(ChatAttachment attachment) {
        return new ChatAttachmentDto(
                attachment.getId(),
                attachment.getType(),
                attachment.getUrl(),
                attachment.getMimeType(),
                attachment.getPrompt(),
                attachment.getProvider(),
                attachment.getMetadata(),
                attachment.getCreatedAt()
        );
    }

    private void verifyRoomOwner(Room room, Long callerId) {
        Long ownerId = room.getUser() != null ? room.getUser().getId() : null;
        if (ownerId == null || !ownerId.equals(callerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found");
        }
    }
}
