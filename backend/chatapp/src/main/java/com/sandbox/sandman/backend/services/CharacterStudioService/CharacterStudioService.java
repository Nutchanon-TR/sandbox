package com.sandbox.sandman.backend.services.CharacterStudioService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sandbox.sandman.backend.model.dto.MessageDto.CharacterCreateRequestDto;
import com.sandbox.sandman.backend.model.dto.MessageDto.CharacterDto;
import com.sandbox.sandman.backend.model.dto.MessageDto.CharacterUpdateRequestDto;
import com.sandbox.sandman.backend.model.entity.MessageEntity.AiContext;
import com.sandbox.sandman.backend.model.entity.MessageEntity.User;
import com.sandbox.sandman.backend.repositories.MessageRepository.AiContextRepository;
import com.sandbox.sandman.backend.repositories.MessageRepository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CharacterStudioService {

    public static final String DEFAULT_TRIGGER_RULES = """
            {
              "photoKeywords": ["ถ่ายรูป", "ส่งรูป", "ขอดูรูป", "ถ่ายมาให้ดู"],
              "activityKeywords": ["ทำอะไรอยู่", "ตอนนี้ทำไร", "อยู่ไหน", "ทำอะไรตอนนี้"]
            }
            """;

    private final AiContextRepository aiContextRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

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
        aiContext.setAvatarUrl(blankToNull(request.getAvatarUrl()));
        aiContext.setRole(blankToNull(request.getRole()));
        aiContext.setCharacter(blankToNull(request.getCharacter()));
        aiContext.setBiography(blankToNull(request.getBiography()));
        aiContext.setRule(blankToNull(request.getRule()));
        aiContext.setPosterUrl(blankToNull(request.getPosterUrl()));
        aiContext.setVisibility(normalizeVisibility(request.getVisibility()));
        aiContext.setStyleExamples(normalizeJson(request.getStyleExamples(), "[]"));
        aiContext.setImageEnabled(request.getImageEnabled() == null || request.getImageEnabled());
        aiContext.setImageTriggerRules(normalizeJson(request.getImageTriggerRules(), DEFAULT_TRIGGER_RULES));
        aiContext.setImagePromptTemplate(blankToNull(request.getImagePromptTemplate()));
        aiContext.setFineTuneStatus("not_started");

        return toDto(aiContextRepository.save(aiContext));
    }

    @Transactional
    public CharacterDto updateCharacter(Long userId, Long characterId, CharacterUpdateRequestDto request) {
        AiContext aiContext = getOwnedCharacter(userId, characterId);

        if (request.getAiName() != null && !request.getAiName().isBlank()) {
            aiContext.setAiName(request.getAiName().trim());
        }
        if (request.getAvatarUrl() != null) aiContext.setAvatarUrl(blankToNull(request.getAvatarUrl()));
        if (request.getRole() != null) aiContext.setRole(blankToNull(request.getRole()));
        if (request.getCharacter() != null) aiContext.setCharacter(blankToNull(request.getCharacter()));
        if (request.getBiography() != null) aiContext.setBiography(blankToNull(request.getBiography()));
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
        if (request.getFineTuneStatus() != null) {
            aiContext.setFineTuneStatus(blankToNull(request.getFineTuneStatus()) == null
                    ? "not_started"
                    : request.getFineTuneStatus().trim());
        }
        if (request.getFineTunedModelId() != null) {
            aiContext.setFineTunedModelId(blankToNull(request.getFineTunedModelId()));
        }

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
                aiContext.getRole(),
                aiContext.getCharacter(),
                aiContext.getBiography(),
                aiContext.getRule(),
                aiContext.getPosterUrl(),
                aiContext.getVisibility(),
                aiContext.getStyleExamples(),
                aiContext.getImageEnabled(),
                aiContext.getImageTriggerRules(),
                aiContext.getImagePromptTemplate(),
                aiContext.getFineTuneStatus(),
                aiContext.getFineTunedModelId()
        );
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
}
