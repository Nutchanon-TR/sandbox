package com.sandbox.sandman.backend.controllers.SyncHubController.BlogController;

import com.sandbox.sandman.backend.model.dto.SyncHubDto.AiContextUserDto;
import com.sandbox.sandman.backend.services.SyncHubService.BlogService.ListService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("${app.api.prefix.chat-app}")
@RequiredArgsConstructor
public class ListController {

    private final ListService listService;

    @GetMapping("/blog/list")
    public ResponseEntity<List<AiContextUserDto>> getBlogList() {
        return ResponseEntity.ok(listService.getAiContextsWithUsers());
    }
}
