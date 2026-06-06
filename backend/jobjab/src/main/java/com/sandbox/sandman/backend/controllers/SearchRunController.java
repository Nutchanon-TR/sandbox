package com.sandbox.sandman.backend.controllers;

import com.sandbox.sandman.backend.commonauth.CurrentUser;
import com.sandbox.sandman.backend.model.dto.SearchRunDto;
import com.sandbox.sandman.backend.model.dto.SearchRunEventDto;
import com.sandbox.sandman.backend.model.dto.SearchRunRequest;
import com.sandbox.sandman.backend.services.SearchRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("${app.api.prefix.jobjab}/search-runs")
@RequiredArgsConstructor
public class SearchRunController {
    private final CurrentUser currentUser;
    private final SearchRunService searchRunService;

    @PostMapping
    public ResponseEntity<SearchRunDto> start(@RequestBody(required = false) SearchRunRequest req) {
        return ResponseEntity.ok(searchRunService.start(currentUser.requireUserId(), req));
    }

    @GetMapping
    public ResponseEntity<List<SearchRunDto>> recent() {
        return ResponseEntity.ok(searchRunService.recent(currentUser.requireUserId()));
    }

    @GetMapping("/{runId}")
    public ResponseEntity<SearchRunDto> get(@PathVariable Long runId) {
        return ResponseEntity.ok(searchRunService.get(currentUser.requireUserId(), runId));
    }

    @GetMapping("/{runId}/events")
    public ResponseEntity<List<SearchRunEventDto>> events(@PathVariable Long runId) {
        return ResponseEntity.ok(searchRunService.events(currentUser.requireUserId(), runId));
    }
}
