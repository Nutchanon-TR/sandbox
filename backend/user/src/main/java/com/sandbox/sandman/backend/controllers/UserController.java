package com.sandbox.sandman.backend.controllers;

import com.sandbox.sandman.backend.model.dto.UserResolveRequestDto;
import com.sandbox.sandman.backend.model.dto.UserResolveResponseDto;
import com.sandbox.sandman.backend.services.UserResolutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${app.api.prefix.user}")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserResolutionService userResolutionService;

    public UserController(UserResolutionService userResolutionService) {
        this.userResolutionService = userResolutionService;
    }

    @PostMapping("/sync")
    public ResponseEntity<UserResolveResponseDto> syncUser(@RequestBody UserResolveRequestDto request) {
        log.info("POST /sync received supabaseUid={}", request == null ? "<null>" : request.getSupabaseUid());
        try {
            UserResolveResponseDto response = userResolutionService.resolveUser(request);
            log.info("POST /sync resolved userId={} for supabaseUid={}",
                    response.getUserId(), request.getSupabaseUid());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("POST /sync failed supabaseUid={} error={}",
                    request == null ? "<null>" : request.getSupabaseUid(), e.toString(), e);
            throw e;
        }
    }
}
