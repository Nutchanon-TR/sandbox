package com.sandbox.sandman.backend.controllers;

import com.sandbox.sandman.backend.commonauth.CurrentUser;
import com.sandbox.sandman.backend.model.dto.WeeklyDigestDto;
import com.sandbox.sandman.backend.services.WeeklyDigestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("${app.api.prefix.jobjab}/digests")
@RequiredArgsConstructor
public class WeeklyDigestController {
    private final CurrentUser currentUser;
    private final WeeklyDigestService weeklyDigestService;

    @GetMapping
    public ResponseEntity<List<WeeklyDigestDto>> list() {
        return ResponseEntity.ok(weeklyDigestService.list(currentUser.requireUserId()));
    }

    @PostMapping("/weekly")
    public ResponseEntity<WeeklyDigestDto> generateWeekly() {
        return ResponseEntity.ok(weeklyDigestService.generate(currentUser.requireUserId()));
    }
}
