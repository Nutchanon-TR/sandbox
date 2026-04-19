package com.sandbox.sandman.backend.controllers.SyncHubController.CardController;

import com.sandbox.sandman.backend.model.dto.SyncHubDto.AiDetailDto;
import com.sandbox.sandman.backend.services.SyncHubService.CardService.DetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${app.api.prefix.chat-app}")
@RequiredArgsConstructor
public class DetailController {

    private final DetailService detailService;

    @GetMapping("/blog/detail/{aiId}")
    public ResponseEntity<AiDetailDto> getDetail(@PathVariable Long aiId) {
        return ResponseEntity.ok(detailService.getDetail(aiId));
    }
}
