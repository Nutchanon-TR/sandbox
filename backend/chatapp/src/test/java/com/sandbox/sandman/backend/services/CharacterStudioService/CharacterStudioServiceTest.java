package com.sandbox.sandman.backend.services.CharacterStudioService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sandbox.sandman.backend.model.dto.MessageDto.CharacterCreateRequestDto;
import com.sandbox.sandman.backend.model.dto.MessageDto.CharacterUpdateRequestDto;
import com.sandbox.sandman.backend.model.entity.MessageEntity.AiContext;
import com.sandbox.sandman.backend.model.entity.MessageEntity.User;
import com.sandbox.sandman.backend.repositories.MessageRepository.AiContextRepository;
import com.sandbox.sandman.backend.repositories.MessageRepository.UserRepository;
import com.sandbox.sandman.backend.services.PersonaFeedService.PersonaFeedScheduleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CharacterStudioServiceTest {

    @Mock
    private AiContextRepository aiContextRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PersonaFeedScheduleService personaFeedScheduleService;

    private CharacterStudioService service;

    @BeforeEach
    void setUp() {
        service = new CharacterStudioService(
                aiContextRepository,
                userRepository,
                new ObjectMapper(),
                personaFeedScheduleService
        );
    }

    @Test
    void createCharacterStoresSystemContextFields() {
        User user = new User();
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(aiContextRepository.save(any(AiContext.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CharacterCreateRequestDto request = new CharacterCreateRequestDto();
        request.setAiName("Kea");
        request.setPersonalityTraits("[\"calm\"]");
        request.setSpeechStyle("Short Thai replies.");
        request.setRelationshipContext("Close friend.");
        request.setMemoryNotes("Likes indie games.");
        request.setResponseBoundaries("Avoid spoilers.");
        request.setSystemContext("Advanced context.");

        var dto = service.createCharacter(1L, request);

        assertThat(dto.getPersonalityTraits()).isEqualTo("[\"calm\"]");
        assertThat(dto.getSpeechStyle()).isEqualTo("Short Thai replies.");
        assertThat(dto.getRelationshipContext()).isEqualTo("Close friend.");
        assertThat(dto.getMemoryNotes()).isEqualTo("Likes indie games.");
        assertThat(dto.getResponseBoundaries()).isEqualTo("Avoid spoilers.");
        assertThat(dto.getSystemContext()).isEqualTo("Advanced context.");
    }

    @Test
    void updateCharacterRejectsInvalidPersonalityTraitsJson() {
        User owner = new User();
        owner.setId(1L);
        AiContext aiContext = new AiContext();
        aiContext.setId(10L);
        aiContext.setCreatedByUser(owner);
        aiContext.setAiName("Kea");

        when(aiContextRepository.findActiveByIdAndOwner(10L, 1L)).thenReturn(Optional.of(aiContext));

        CharacterUpdateRequestDto request = new CharacterUpdateRequestDto();
        request.setPersonalityTraits("{not-json");

        assertThatThrownBy(() -> service.updateCharacter(1L, 10L, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
