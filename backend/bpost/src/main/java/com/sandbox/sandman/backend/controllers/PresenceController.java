package com.sandbox.sandman.backend.controllers;

import com.sandbox.sandman.backend.services.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("${app.api.prefix.b-post}/presence")
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService presenceService;

    @GetMapping("/online")
    public ResponseEntity<Set<Long>> online() {
        return ResponseEntity.ok(presenceService.onlineUsers());
    }
}
