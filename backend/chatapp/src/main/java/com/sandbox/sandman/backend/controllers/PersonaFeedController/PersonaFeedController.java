package com.sandbox.sandman.backend.controllers.PersonaFeedController;

import com.sandbox.sandman.backend.commonauth.CurrentUser;
import com.sandbox.sandman.backend.model.dto.PersonaFeedDto.CursorPageDto;
import com.sandbox.sandman.backend.model.dto.PersonaFeedDto.PersonaFeedFollowResponseDto;
import com.sandbox.sandman.backend.model.dto.PersonaFeedDto.PersonaFeedPersonaProfileDto;
import com.sandbox.sandman.backend.model.dto.PersonaFeedDto.PersonaFeedPostDto;
import com.sandbox.sandman.backend.services.PersonaFeedService.PersonaFeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${app.api.prefix.chat-app}/persona-feed")
@RequiredArgsConstructor
public class PersonaFeedController {

    private final PersonaFeedService personaFeedService;
    private final CurrentUser currentUser;

    @GetMapping("/posts")
    public ResponseEntity<CursorPageDto<PersonaFeedPostDto>> feed(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(personaFeedService.feed(currentUser.requireUserId(), beforeId, limit));
    }

    @GetMapping("/personas/{personaId}")
    public ResponseEntity<PersonaFeedPersonaProfileDto> profile(@PathVariable Long personaId) {
        return ResponseEntity.ok(personaFeedService.profile(currentUser.requireUserId(), personaId));
    }

    @GetMapping("/personas/{personaId}/posts")
    public ResponseEntity<CursorPageDto<PersonaFeedPostDto>> postsByPersona(
            @PathVariable Long personaId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(personaFeedService.postsByPersona(
                currentUser.requireUserId(),
                personaId,
                beforeId,
                limit
        ));
    }

    @PostMapping("/personas/{personaId}/follow")
    public ResponseEntity<PersonaFeedFollowResponseDto> follow(@PathVariable Long personaId) {
        return ResponseEntity.ok(personaFeedService.follow(currentUser.requireUserId(), personaId));
    }
}
