package com.sandbox.sandman.backend.controllers.SyncHubController;

import com.sandbox.sandman.backend.model.dto.SyncHubDto.AiContextUserDto;
import com.sandbox.sandman.backend.services.SyncHubService.CardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("${app.api.prefix.chat-app}")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;

    @GetMapping("blog/inquiry")
    public ResponseEntity<List<AiContextUserDto>> getAiContextsWithUsers() {
        return ResponseEntity.ok(cardService.getAiContextsWithUsers());
    }
}
