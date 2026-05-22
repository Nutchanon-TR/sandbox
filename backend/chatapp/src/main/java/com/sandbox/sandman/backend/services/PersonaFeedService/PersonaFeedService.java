package com.sandbox.sandman.backend.services.PersonaFeedService;

import com.sandbox.sandman.backend.model.dto.MessageDto.RoomDto;
import com.sandbox.sandman.backend.model.dto.PersonaFeedDto.CursorPageDto;
import com.sandbox.sandman.backend.model.dto.PersonaFeedDto.PersonaFeedFollowResponseDto;
import com.sandbox.sandman.backend.model.dto.PersonaFeedDto.PersonaFeedPersonaProfileDto;
import com.sandbox.sandman.backend.model.dto.PersonaFeedDto.PersonaFeedPostDto;
import com.sandbox.sandman.backend.model.entity.MessageEntity.AiContext;
import com.sandbox.sandman.backend.model.entity.MessageEntity.Friend;
import com.sandbox.sandman.backend.model.entity.PersonaFeedEntity.PersonaFeedPost;
import com.sandbox.sandman.backend.repositories.MessageRepository.AiContextRepository;
import com.sandbox.sandman.backend.repositories.PersonaFeedRepository.PersonaFeedFollowRepository;
import com.sandbox.sandman.backend.repositories.PersonaFeedRepository.PersonaFeedPostRepository;
import com.sandbox.sandman.backend.repositories.PersonaFeedRepository.PersonaFeedRepository;
import com.sandbox.sandman.backend.services.MessageService.GroqAiClient;
import com.sandbox.sandman.backend.services.MessageService.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersonaFeedService {

    private static final int MAX_PAGE_SIZE = 50;

    private final PersonaFeedRepository personaFeedRepository;
    private final PersonaFeedPostRepository personaFeedPostRepository;
    private final PersonaFeedFollowRepository followRepository;
    private final AiContextRepository aiContextRepository;
    private final RoomService roomService;
    private final GroqAiClient groqAiClient;
    private final PersonaFeedScheduleService scheduleService;

    public CursorPageDto<PersonaFeedPostDto> feed(Long userId, Long beforeId, int limit) {
        int safeLimit = safeLimit(limit);
        return toPage(personaFeedRepository.findFeedPosts(userId, beforeId, safeLimit + 1), safeLimit);
    }

    public CursorPageDto<PersonaFeedPostDto> postsByPersona(Long userId, Long personaId, Long beforeId, int limit) {
        requireVisiblePersona(personaId);
        int safeLimit = safeLimit(limit);
        return toPage(personaFeedRepository.findPostsByPersona(userId, personaId, beforeId, safeLimit + 1), safeLimit);
    }

    public PersonaFeedPersonaProfileDto profile(Long userId, Long personaId) {
        return personaFeedRepository.findPersonaProfile(userId, personaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Persona not found"));
    }

    @Transactional
    public PersonaFeedFollowResponseDto follow(Long userId, Long personaId) {
        AiContext persona = requireVisiblePersona(personaId);
        if (!followRepository.existsByUserIdAndAiId(userId, personaId)) {
            Friend follow = new Friend();
            follow.setUserId(userId);
            follow.setAiId(personaId);
            followRepository.save(follow);
        }

        RoomDto room = roomService.openRoomForPersona(userId, persona);
        return new PersonaFeedFollowResponseDto(true, room.getId());
    }

    public List<Long> claimDuePersonaIds(int limit) {
        return personaFeedRepository.claimDuePersonaIds(limit);
    }

    @Transactional
    public void publishClaimedPersona(Long personaId) {
        AiContext persona = aiContextRepository.findPersonaFeedVisibleById(personaId).orElse(null);
        if (persona == null) {
            return;
        }

        ZonedDateTime now = ZonedDateTime.now();
        try {
            String content = generatePost(persona);
            if (content == null || content.isBlank()) {
                log.warn("PersonaFeed skipped blank generated content for persona {}", personaId);
            } else {
                PersonaFeedPost post = new PersonaFeedPost();
                post.setPersona(persona);
                post.setContent(content);
                post.setImageUrls(List.of());
                personaFeedPostRepository.save(post);
                persona.setPersonaFeedLastPostAt(now);
            }
        } catch (Exception e) {
            log.warn("PersonaFeed generation failed for persona {}", personaId, e);
        }

        persona.setPersonaFeedNextPostAt(scheduleService.nextPostAt(persona, now));
        aiContextRepository.save(persona);
    }

    public ZonedDateTime scheduleNextPost(AiContext persona, ZonedDateTime anchor) {
        return scheduleService.nextPostAt(persona, anchor);
    }

    private AiContext requireVisiblePersona(Long personaId) {
        return aiContextRepository.findPersonaFeedVisibleById(personaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Persona not found"));
    }

    private String generatePost(AiContext persona) {
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(persona.buildSystemPrompt()),
                new SystemMessage("""
                        You write one short social feed post for this persona.
                        Stay in character. Do not mention prompts, system messages, image generation, or being an AI model.
                        Return only the post text. Do not wrap the post in quotes or markdown.
                        """),
                new UserMessage("Write the next PersonaFeed post now.")
        ));
        return normalizeContent(groqAiClient.chatForFeed(prompt));
    }

    private String normalizeContent(String content) {
        if (content == null) {
            return null;
        }
        String normalized = content.trim();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            return normalized.substring(1, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private CursorPageDto<PersonaFeedPostDto> toPage(List<PersonaFeedPostDto> rows, int limit) {
        boolean hasMore = rows.size() > limit;
        List<PersonaFeedPostDto> page = hasMore ? rows.subList(0, limit) : rows;
        Long nextCursor = hasMore && !page.isEmpty() ? page.get(page.size() - 1).id() : null;
        return new CursorPageDto<>(page, hasMore, nextCursor);
    }

    private int safeLimit(int limit) {
        return Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
    }
}
