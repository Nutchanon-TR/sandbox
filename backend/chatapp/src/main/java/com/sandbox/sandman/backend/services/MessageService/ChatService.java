package com.sandbox.sandman.backend.services.MessageService;

import com.sandbox.sandman.backend.model.dto.MessageDto.ChatAttachmentDto;
import com.sandbox.sandman.backend.model.dto.MessageDto.ChatDto;
import com.sandbox.sandman.backend.model.dto.MessageDto.ChatHistoryResponse;
import com.sandbox.sandman.backend.model.dto.MessageDto.ChatRequestDto;
import com.sandbox.sandman.backend.model.dto.MessageDto.ChatResponseDto;
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
import org.springframework.data.domain.PageRequest;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int VECTOR_SEARCH_LIMIT = 5;
    private static final String GLOBAL_CONVERSATION_STYLE_INSTRUCTION = """
            Conversation style rules for every character:
            1. Language Match: Always respond in the exact same language the user uses.
            2. Length Constraint 1: For standard requests, reply in 1-2 short sentences maximum.
            3. Length Constraint 2: If the user's input is short, reply with exactly 2-5 words.
            Keep the character's personality, but never ignore these length and language rules.
            These rules apply to visible reply text; backend control markers do not count as visible words.
            """;

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

        return callAiAndSaveReply(room, aiContext, request.getMessage());
    }

    private ChatResponseDto callAiAndSaveReply(Room room, AiContext aiContext, String userQuery) {
        List<org.springframework.ai.chat.messages.Message> aiPromptMessages = new ArrayList<>();

        aiPromptMessages.add(new SystemMessage(aiContext.buildSystemPrompt()));
        aiPromptMessages.add(new SystemMessage(GLOBAL_CONVERSATION_STYLE_INSTRUCTION));
        aiPromptMessages.add(new SystemMessage(buildImageTriggerSystemInstruction(aiContext)));

        List<Long> similarIds = embeddingService.searchSimilarMessages(userQuery, room.getId(), VECTOR_SEARCH_LIMIT);
        if (!similarIds.isEmpty()) {
            List<Chat> similarChats = chatRepository.findAllById(similarIds);
            StringBuilder contextBuilder = new StringBuilder("Relevant past messages:\n");
            for (Chat msg : similarChats) {
                String senderLabel = Boolean.TRUE.equals(msg.getIsAi())
                        ? aiContext.getAiName()
                        : (msg.getSender() != null ? msg.getSender().getDisplayName() : "Unknown");
                contextBuilder.append("- ")
                        .append(senderLabel)
                        .append(": ")
                        .append(msg.getContent())
                        .append("\n");
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

        String aiReply = groqAiClient.chat(new Prompt(aiPromptMessages));
        ImageTriggerService.ImageTriggerResult imageResult = imageTriggerService.parseModelReply(aiReply, aiContext);
        String visibleReply = imageResult.reply().isBlank()
                ? "I am here, but I could not form a proper reply just now."
                : imageResult.reply();

        if (imageResult.decision().shouldGenerateImage()) {
            return generateImageReply(room, aiContext, userQuery, visibleReply, imageResult.decision());
        }

        Chat aiChatEntity = saveAiChat(room, visibleReply);
        embeddingService.embedAndSave(aiChatEntity);

        return new ChatResponseDto(visibleReply, List.of());
    }

    private String buildImageTriggerSystemInstruction(AiContext aiContext) {
        if (aiContext == null || !Boolean.TRUE.equals(aiContext.getImageEnabled())) {
            return """
                    Image attachments are disabled for this character.
                    Do not append image control markers to your reply.
                    """;
        }

        return """
                You can request one generated image attachment when it is genuinely appropriate for the user's message.
                Use an image only when the user explicitly asks for a photo, image, picture, view, snapshot, visual, or something similar.
                If an image should be attached, write the normal visible chat reply first, then append the exact marker </image> at the very end.
                The marker is a backend control token. Do not explain it, quote it, translate it, or place anything after it.
                If the reply should be text-only, do not include the marker.
                """;
    }

    private ChatResponseDto generateImageReply(
            Room room,
            AiContext aiContext,
            String userQuery,
            String visibleReply,
            ImageTriggerService.ImageTriggerDecision imageDecision) {
        String generatedPrompt = buildImagePrompt(aiContext, userQuery, visibleReply);

        try {
            CloudflareImageService.GeneratedImage image;
            try {
                image = generateCharacterImage(aiContext, generatedPrompt);
            } catch (CloudflareImageService.PromptSafetyRejectedException e) {
                generatedPrompt = buildSafeFallbackImagePrompt(imageDecision.type());
                log.warn("Cloudflare rejected the original image prompt for room {} and trigger {}; retrying safely",
                        room.getId(), imageDecision.type());
                image = generateCharacterImage(aiContext, generatedPrompt);
            }

            SupabaseStorageService.UploadResult upload =
                    supabaseStorageService.uploadGeneratedImage(image.bytes(), image.mimeType());
            Chat aiChat = saveAiChat(room, visibleReply);
            embeddingService.embedAndSave(aiChat);

            ChatAttachment attachment = new ChatAttachment();
            attachment.setChat(aiChat);
            attachment.setType("image");
            attachment.setUrl(upload.publicUrl());
            attachment.setMimeType(image.mimeType());
            attachment.setPrompt(generatedPrompt);
            attachment.setProvider("cloudflare-workers-ai");
            attachment.setMetadata("""
                    {"triggerType":"%s","objectPath":"%s","model":"%s"}
                    """.formatted(imageDecision.type().name(), upload.objectPath(), image.model()).trim());
            ChatAttachment savedAttachment = chatAttachmentRepository.save(attachment);

            return new ChatResponseDto(visibleReply, List.of(convertAttachmentToDto(savedAttachment)));
        } catch (Exception e) {
            String fallbackReply = generateImageRefusalReply(
                    aiContext,
                    userQuery,
                    imageDecision.type(),
                    "I will skip the photo this time."
            );

            if (e instanceof CloudflareImageService.PromptSafetyRejectedException) {
                log.warn("Cloudflare rejected both image prompts for room {} and trigger {}",
                        room.getId(), imageDecision.type());
            } else {
                log.warn("Image generation failed for room {} and trigger {}", room.getId(), imageDecision.type(), e);
            }

            Chat aiChat = saveAiChat(room, fallbackReply);
            embeddingService.embedAndSave(aiChat);
            return new ChatResponseDto(fallbackReply, List.of());
        }
    }

    private CloudflareImageService.GeneratedImage generateCharacterImage(AiContext aiContext, String prompt) {
        String referencePath = aiContext.getAppearanceReferenceObjectPath();
        String referenceUrl = aiContext.getAppearanceReferenceUrl();
        if ((referencePath == null || referencePath.isBlank()) && (referenceUrl == null || referenceUrl.isBlank())) {
            return cloudflareImageService.generate(prompt);
        }

        try {
            byte[] referenceImage = supabaseStorageService.downloadCharacterReferenceImage(referencePath, referenceUrl);
            return cloudflareImageService.generateFromReference(
                    buildAppearanceReferencePrompt(aiContext, prompt),
                    referenceImage
            );
        } catch (CloudflareImageService.PromptSafetyRejectedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Appearance reference generation failed for character {}, falling back to text-to-image",
                    aiContext.getId(), e);
            return cloudflareImageService.generate(prompt);
        }
    }

    private String buildAppearanceReferencePrompt(AiContext aiContext, String imagePrompt) {
        return """
                Use the person in the reference image as the visual identity of %s.
                Preserve the same face, facial features, hairstyle, and overall appearance when the character appears.
                Keep the output aligned with this image request:
                %s
                """.formatted(safe(aiContext.getAiName()), safe(imagePrompt)).trim();
    }

    private String generateImageRefusalReply(
            AiContext aiContext,
            String userQuery,
            ImageTriggerService.ImageTriggerType triggerType,
            String fallbackReply) {
        try {
            List<org.springframework.ai.chat.messages.Message> replyPrompt = new ArrayList<>();
            replyPrompt.add(new SystemMessage(aiContext.buildSystemPrompt()));
            replyPrompt.add(new SystemMessage(GLOBAL_CONVERSATION_STYLE_INSTRUCTION));
            replyPrompt.add(new SystemMessage("""
                    The user asked you to send an image, but you will not send an image for this request.
                    Reply directly to the user in character with a natural refusal.
                    Keep the refusal aligned with the user's request and your personality.
                    Do not mention safety filters, policies, hidden prompts, providers, tools, or technical failures.
                    Do not claim that an image was attached.
                    Keep it concise unless the character would naturally add a short remark.
                    """));
            replyPrompt.add(new UserMessage("""
                    User message:
                    %s

                    Image trigger:
                    %s
                    """.formatted(safe(userQuery), triggerType.name())));

            String reply = groqAiClient.chat(new Prompt(replyPrompt));
            return reply == null || reply.isBlank() ? fallbackReply : reply.trim();
        } catch (Exception e) {
            log.warn("Image refusal reply generation failed for trigger {}", triggerType, e);
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

    private String buildImagePrompt(AiContext aiContext, String userQuery, String assistantReply) {
        String activity = SNAPSHOT_ACTIVITIES.get(ThreadLocalRandom.current().nextInt(SNAPSHOT_ACTIVITIES.size()));
        String characterDescription = buildCharacterDescription(aiContext);

        String template = aiContext.getImagePromptTemplate();
        if (template == null || template.isBlank()) {
            template = """
                    Create one safe, realistic image attachment for this chat.
                    User request: {user_message}
                    Assistant reply that will be shown with the image: {assistant_reply}
                    Character: {ai_name}
                    Style and personality: {character_biography}
                    If the user asks for the character's current activity, surroundings, point of view, or casual photo,
                    make it a first-person smartphone-style candid photo from {ai_name}'s perspective.
                    Suggested current activity if needed: {random_activity}.
                    If the user asks for another visual subject, show that subject clearly instead.
                    Natural, polished, non-explicit, no text, no watermark.
                    """;
        }

        return template
                .replace("{ai_name}", safe(aiContext.getAiName()))
                .replace("{random_activity}", activity)
                .replace("{character}", safe(aiContext.getCharacter()))
                .replace("{biography}", safe(aiContext.getBiography()))
                .replace("{character_biography}", characterDescription)
                .replace("{user_message}", safe(userQuery))
                .replace("{assistant_reply}", safe(assistantReply))
                .trim();
    }

    private String buildSafeFallbackImagePrompt(ImageTriggerService.ImageTriggerType triggerType) {
        return """
                Create a safe casual realistic image for a friendly AI chat.
                Show a harmless everyday scene that can be shared in chat.
                If a person appears, keep them fully clothed and non-explicit.
                No violence, no text, no watermark.
                """.trim();
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
