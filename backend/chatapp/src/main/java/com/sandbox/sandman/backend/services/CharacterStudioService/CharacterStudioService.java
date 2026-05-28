package com.sandbox.sandman.backend.services.CharacterStudioService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sandbox.sandman.backend.model.dto.MessageDto.CharacterCreateRequestDto;
import com.sandbox.sandman.backend.model.dto.MessageDto.CharacterDto;
import com.sandbox.sandman.backend.model.dto.MessageDto.CharacterUpdateRequestDto;
import com.sandbox.sandman.backend.model.entity.MessageEntity.AiContext;
import com.sandbox.sandman.backend.model.entity.MessageEntity.User;
import com.sandbox.sandman.backend.repositories.MessageRepository.AiContextRepository;
import com.sandbox.sandman.backend.repositories.MessageRepository.UserRepository;
import com.sandbox.sandman.backend.services.PersonaFeedService.PersonaFeedScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CharacterStudioService {

    public static final String DEFAULT_AVATAR_URL = "/ai_avatar.png";

    public static final String DEFAULT_TRIGGER_RULES = """
            {
              "photoKeywords": ["ถ่ายรูป", "ส่งรูป", "ขอดูรูป", "ถ่ายมาให้ดู"],
              "activityKeywords": ["ทำอะไรอยู่", "ตอนนี้ทำไร", "อยู่ไหน", "ทำอะไรตอนนี้"]
            }
            """;

    private final AiContextRepository aiContextRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final PersonaFeedScheduleService personaFeedScheduleService;

    public List<CharacterDto> listCharacters(Long userId) {
        return aiContextRepository.findActiveByOwner(userId).stream()
                .map(this::toDto)
                .toList();
    }

    public CharacterDto getCharacter(Long userId, Long characterId) {
        return toDto(getOwnedCharacter(userId, characterId));
    }

    @Transactional
    public CharacterDto createCharacter(Long userId, CharacterCreateRequestDto request) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        AiContext aiContext = new AiContext();
        aiContext.setCreatedByUser(owner);
        aiContext.setAiName(request.getAiName().trim());
        aiContext.setAvatarUrl(defaultAvatarIfBlank(request.getAvatarUrl()));
        aiContext.setAppearanceReferenceUrl(blankToNull(request.getAppearanceReferenceUrl()));
        aiContext.setAppearanceReferenceObjectPath(blankToNull(request.getAppearanceReferenceObjectPath()));
        aiContext.setRole(blankToNull(request.getRole()));
        aiContext.setCharacter(blankToNull(request.getCharacter()));
        aiContext.setPersonalityTraits(normalizeJson(request.getPersonalityTraits(), "[]"));
        aiContext.setBiography(blankToNull(request.getBiography()));
        aiContext.setSpeechStyle(blankToNull(request.getSpeechStyle()));
        aiContext.setRelationshipContext(blankToNull(request.getRelationshipContext()));
        aiContext.setMemoryNotes(blankToNull(request.getMemoryNotes()));
        aiContext.setResponseBoundaries(blankToNull(request.getResponseBoundaries()));
        aiContext.setSystemContext(blankToNull(request.getSystemContext()));
        aiContext.setRule(blankToNull(request.getRule()));
        aiContext.setPosterUrl(blankToNull(request.getPosterUrl()));
        aiContext.setVisibility(normalizeVisibility(request.getVisibility()));
        aiContext.setStyleExamples(normalizeJson(request.getStyleExamples(), "[]"));
        aiContext.setImageEnabled(request.getImageEnabled() == null || request.getImageEnabled());
        aiContext.setImageTriggerRules(normalizeJson(request.getImageTriggerRules(), DEFAULT_TRIGGER_RULES));
        aiContext.setImagePromptTemplate(blankToNull(request.getImagePromptTemplate()));
        aiContext.setFineTuneStatus("not_started");
        applyPersonaFeedCreate(aiContext, request);

        return toDto(aiContextRepository.save(aiContext));
    }

    @Transactional
    public CharacterDto updateCharacter(Long userId, Long characterId, CharacterUpdateRequestDto request) {
        AiContext aiContext = getOwnedCharacter(userId, characterId);

        if (request.getAiName() != null && !request.getAiName().isBlank()) {
            aiContext.setAiName(request.getAiName().trim());
        }
        if (request.getAvatarUrl() != null) aiContext.setAvatarUrl(defaultAvatarIfBlank(request.getAvatarUrl()));
        if (request.getAppearanceReferenceUrl() != null) {
            aiContext.setAppearanceReferenceUrl(blankToNull(request.getAppearanceReferenceUrl()));
        }
        if (request.getAppearanceReferenceObjectPath() != null) {
            aiContext.setAppearanceReferenceObjectPath(blankToNull(request.getAppearanceReferenceObjectPath()));
        }
        if (request.getRole() != null) aiContext.setRole(blankToNull(request.getRole()));
        if (request.getCharacter() != null) aiContext.setCharacter(blankToNull(request.getCharacter()));
        if (request.getPersonalityTraits() != null) {
            aiContext.setPersonalityTraits(normalizeJson(request.getPersonalityTraits(), "[]"));
        }
        if (request.getBiography() != null) aiContext.setBiography(blankToNull(request.getBiography()));
        if (request.getSpeechStyle() != null) aiContext.setSpeechStyle(blankToNull(request.getSpeechStyle()));
        if (request.getRelationshipContext() != null) {
            aiContext.setRelationshipContext(blankToNull(request.getRelationshipContext()));
        }
        if (request.getMemoryNotes() != null) aiContext.setMemoryNotes(blankToNull(request.getMemoryNotes()));
        if (request.getResponseBoundaries() != null) {
            aiContext.setResponseBoundaries(blankToNull(request.getResponseBoundaries()));
        }
        if (request.getSystemContext() != null) aiContext.setSystemContext(blankToNull(request.getSystemContext()));
        if (request.getRule() != null) aiContext.setRule(blankToNull(request.getRule()));
        if (request.getPosterUrl() != null) aiContext.setPosterUrl(blankToNull(request.getPosterUrl()));
        if (request.getVisibility() != null) aiContext.setVisibility(normalizeVisibility(request.getVisibility()));
        if (request.getStyleExamples() != null) aiContext.setStyleExamples(normalizeJson(request.getStyleExamples(), "[]"));
        if (request.getImageEnabled() != null) aiContext.setImageEnabled(request.getImageEnabled());
        if (request.getImageTriggerRules() != null) {
            aiContext.setImageTriggerRules(normalizeJson(request.getImageTriggerRules(), DEFAULT_TRIGGER_RULES));
        }
        if (request.getImagePromptTemplate() != null) {
            aiContext.setImagePromptTemplate(blankToNull(request.getImagePromptTemplate()));
        }
        applyPersonaFeedUpdate(aiContext, request);

        return toDto(aiContextRepository.save(aiContext));
    }

    @Transactional
    public void deleteCharacter(Long userId, Long characterId) {
        AiContext aiContext = getOwnedCharacter(userId, characterId);
        aiContext.setVisibility("archived");
        aiContextRepository.save(aiContext);
    }

    public AiContext getOwnedCharacter(Long userId, Long characterId) {
        return aiContextRepository.findActiveByIdAndOwner(characterId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found"));
    }

    public CharacterDto toDto(AiContext aiContext) {
        Long ownerId = aiContext.getCreatedByUser() != null ? aiContext.getCreatedByUser().getId() : null;
        return new CharacterDto(
                aiContext.getId(),
                ownerId,
                aiContext.getAiName(),
                aiContext.getAvatarUrl(),
                aiContext.getAppearanceReferenceUrl(),
                aiContext.getAppearanceReferenceObjectPath(),
                aiContext.getRole(),
                aiContext.getCharacter(),
                aiContext.getPersonalityTraits(),
                aiContext.getBiography(),
                aiContext.getSpeechStyle(),
                aiContext.getRelationshipContext(),
                aiContext.getMemoryNotes(),
                aiContext.getResponseBoundaries(),
                aiContext.getSystemContext(),
                aiContext.getRule(),
                aiContext.getPosterUrl(),
                aiContext.getVisibility(),
                aiContext.getStyleExamples(),
                aiContext.getImageEnabled(),
                aiContext.getImageTriggerRules(),
                aiContext.getImagePromptTemplate(),
                aiContext.getFineTuneStatus(),
                aiContext.getFineTunedModelId(),
                aiContext.getPersonaFeedEnabled(),
                aiContext.getPersonaFeedMinIntervalHours(),
                aiContext.getPersonaFeedMaxIntervalHours(),
                timeToText(aiContext.getPersonaFeedWindowStart()),
                timeToText(aiContext.getPersonaFeedWindowEnd()),
                aiContext.getPersonaFeedTimezone()
        );
    }

    private void applyPersonaFeedCreate(AiContext aiContext, CharacterCreateRequestDto request) {
        aiContext.setPersonaFeedMinIntervalHours(defaultInterval(
                request.getPersonaFeedMinIntervalHours(),
                PersonaFeedScheduleService.DEFAULT_MIN_INTERVAL_HOURS
        ));
        aiContext.setPersonaFeedMaxIntervalHours(defaultInterval(
                request.getPersonaFeedMaxIntervalHours(),
                PersonaFeedScheduleService.DEFAULT_MAX_INTERVAL_HOURS
        ));
        aiContext.setPersonaFeedWindowStart(parseOptionalTime(request.getPersonaFeedWindowStart()));
        aiContext.setPersonaFeedWindowEnd(parseOptionalTime(request.getPersonaFeedWindowEnd()));
        aiContext.setPersonaFeedTimezone(normalizeTimezone(request.getPersonaFeedTimezone()));
        validatePersonaFeedSchedule(aiContext);
        updatePersonaFeedEnabled(aiContext, Boolean.TRUE.equals(request.getPersonaFeedEnabled()), true);
    }

    private void applyPersonaFeedUpdate(AiContext aiContext, CharacterUpdateRequestDto request) {
        boolean scheduleChanged = false;
        if (request.getPersonaFeedMinIntervalHours() != null) {
            aiContext.setPersonaFeedMinIntervalHours(request.getPersonaFeedMinIntervalHours());
            scheduleChanged = true;
        }
        if (request.getPersonaFeedMaxIntervalHours() != null) {
            aiContext.setPersonaFeedMaxIntervalHours(request.getPersonaFeedMaxIntervalHours());
            scheduleChanged = true;
        }
        if (request.getPersonaFeedWindowStart() != null) {
            aiContext.setPersonaFeedWindowStart(parseOptionalTime(request.getPersonaFeedWindowStart()));
            scheduleChanged = true;
        }
        if (request.getPersonaFeedWindowEnd() != null) {
            aiContext.setPersonaFeedWindowEnd(parseOptionalTime(request.getPersonaFeedWindowEnd()));
            scheduleChanged = true;
        }
        if (request.getPersonaFeedTimezone() != null) {
            aiContext.setPersonaFeedTimezone(normalizeTimezone(request.getPersonaFeedTimezone()));
            scheduleChanged = true;
        }

        validatePersonaFeedSchedule(aiContext);
        if (request.getPersonaFeedEnabled() != null) {
            updatePersonaFeedEnabled(aiContext, request.getPersonaFeedEnabled(), true);
            return;
        }

        if (!isPublic(aiContext) && Boolean.TRUE.equals(aiContext.getPersonaFeedEnabled())) {
            updatePersonaFeedEnabled(aiContext, false, false);
            return;
        }

        if (scheduleChanged && Boolean.TRUE.equals(aiContext.getPersonaFeedEnabled())) {
            aiContext.setPersonaFeedNextPostAt(personaFeedScheduleService.nextPostAt(aiContext, ZonedDateTime.now()));
        }
    }

    private void updatePersonaFeedEnabled(AiContext aiContext, boolean enabled, boolean rejectPrivateEnable) {
        if (!enabled) {
            aiContext.setPersonaFeedEnabled(false);
            aiContext.setPersonaFeedNextPostAt(null);
            return;
        }
        if (!isPublic(aiContext)) {
            if (rejectPrivateEnable) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PersonaFeed requires public visibility");
            }
            aiContext.setPersonaFeedEnabled(false);
            aiContext.setPersonaFeedNextPostAt(null);
            return;
        }

        aiContext.setPersonaFeedEnabled(true);
        if (aiContext.getPersonaFeedNextPostAt() == null) {
            aiContext.setPersonaFeedNextPostAt(personaFeedScheduleService.nextPostAt(aiContext, ZonedDateTime.now()));
        }
    }

    private void validatePersonaFeedSchedule(AiContext aiContext) {
        Integer minHours = aiContext.getPersonaFeedMinIntervalHours();
        Integer maxHours = aiContext.getPersonaFeedMaxIntervalHours();
        if (minHours == null || maxHours == null || minHours <= 0 || maxHours < minHours) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid PersonaFeed interval range");
        }
        boolean hasWindowStart = aiContext.getPersonaFeedWindowStart() != null;
        boolean hasWindowEnd = aiContext.getPersonaFeedWindowEnd() != null;
        if (hasWindowStart != hasWindowEnd) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PersonaFeed posting window requires start and end");
        }
    }

    private LocalTime parseOptionalTime(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return LocalTime.parse(normalized);
        } catch (DateTimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid PersonaFeed posting time");
        }
    }

    private String normalizeTimezone(String timezone) {
        String normalized = blankToNull(timezone);
        if (normalized == null) {
            return PersonaFeedScheduleService.DEFAULT_TIMEZONE;
        }
        try {
            return ZoneId.of(normalized).getId();
        } catch (DateTimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid PersonaFeed timezone");
        }
    }

    private int defaultInterval(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private boolean isPublic(AiContext aiContext) {
        return "public".equals(aiContext.getVisibility());
    }

    private String timeToText(LocalTime time) {
        return time == null ? null : time.toString();
    }

    private String normalizeJson(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            objectMapper.readTree(value);
            return value.trim();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON field");
        }
    }

    private String normalizeVisibility(String visibility) {
        if (visibility == null || visibility.isBlank()) {
            return "private";
        }
        String normalized = visibility.trim().toLowerCase();
        if (!List.of("private", "public", "unlisted", "archived").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported visibility");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private static String defaultAvatarIfBlank(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? DEFAULT_AVATAR_URL : normalized;
    }
}
