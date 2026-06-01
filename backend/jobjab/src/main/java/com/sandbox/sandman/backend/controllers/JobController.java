package com.sandbox.sandman.backend.controllers;

import com.sandbox.sandman.backend.commonauth.CurrentUser;
import com.sandbox.sandman.backend.model.dto.JobDto;
import com.sandbox.sandman.backend.model.dto.PageResponse;
import com.sandbox.sandman.backend.services.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${app.api.prefix.jobjab}/jobs")
@RequiredArgsConstructor
public class JobController {
    private final CurrentUser currentUser;
    private final JobService jobService;

    @GetMapping
    public ResponseEntity<PageResponse<JobDto>> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "false") boolean nearbyFirst,
            @RequestParam(defaultValue = "false") boolean trackedOnly) {
        return ResponseEntity.ok(jobService.list(currentUser.requireUserId(), q, limit, nearbyFirst, trackedOnly));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobDto> get(@PathVariable Long jobId) {
        return ResponseEntity.ok(jobService.get(currentUser.requireUserId(), jobId));
    }
}
