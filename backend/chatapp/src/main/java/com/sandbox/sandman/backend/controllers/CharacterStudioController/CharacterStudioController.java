package com.sandbox.sandman.backend.controllers.CharacterStudioController;

import com.sandbox.sandman.backend.commonauth.CurrentUser;
import com.sandbox.sandman.backend.model.dto.MessageDto.CharacterCreateRequestDto;
import com.sandbox.sandman.backend.model.dto.MessageDto.CharacterDto;
import com.sandbox.sandman.backend.model.dto.MessageDto.CharacterUpdateRequestDto;
import com.sandbox.sandman.backend.services.CharacterStudioService.CharacterStudioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("${app.api.prefix.chat-app}")
@RequiredArgsConstructor
public class CharacterStudioController {

    private final CharacterStudioService characterStudioService;
    private final CurrentUser currentUser;

    @GetMapping("/characters")
    public ResponseEntity<List<CharacterDto>> listCharacters() {
        return ResponseEntity.ok(characterStudioService.listCharacters(currentUser.requireUserId()));
    }

    @PostMapping("/characters")
    public ResponseEntity<CharacterDto> createCharacter(@Valid @RequestBody CharacterCreateRequestDto request) {
        return ResponseEntity.ok(characterStudioService.createCharacter(currentUser.requireUserId(), request));
    }

    @GetMapping("/characters/{characterId}")
    public ResponseEntity<CharacterDto> getCharacter(@PathVariable Long characterId) {
        return ResponseEntity.ok(characterStudioService.getCharacter(currentUser.requireUserId(), characterId));
    }

    @PatchMapping("/characters/{characterId}")
    public ResponseEntity<CharacterDto> updateCharacter(
            @PathVariable Long characterId,
            @RequestBody CharacterUpdateRequestDto request) {
        return ResponseEntity.ok(characterStudioService.updateCharacter(currentUser.requireUserId(), characterId, request));
    }

    @DeleteMapping("/characters/{characterId}")
    public ResponseEntity<Void> deleteCharacter(@PathVariable Long characterId) {
        characterStudioService.deleteCharacter(currentUser.requireUserId(), characterId);
        return ResponseEntity.noContent().build();
    }
}
