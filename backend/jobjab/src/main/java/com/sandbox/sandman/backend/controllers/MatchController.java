package com.sandbox.sandman.backend.controllers;

import com.sandbox.sandman.backend.commonauth.CurrentUser;
import com.sandbox.sandman.backend.model.dto.JobMatchDto;
import com.sandbox.sandman.backend.services.MatchAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${app.api.prefix.jobjab}/jobs/{jobId}/match")
@RequiredArgsConstructor
public class MatchController {
    private final CurrentUser currentUser;
    private final MatchAnalysisService matchAnalysisService;

    @PostMapping
    public ResponseEntity<JobMatchDto> analyze(@PathVariable Long jobId) {
        return ResponseEntity.ok(matchAnalysisService.analyze(currentUser.requireUserId(), jobId));
    }
}
