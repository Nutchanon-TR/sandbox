package com.sandbox.sandman.backend.controllers;

import com.sandbox.sandman.backend.model.dto.ChatDto.UserResolveRequestDto;
import com.sandbox.sandman.backend.model.dto.ChatDto.UserResolveResponseDto;
import com.sandbox.sandman.backend.services.UserResolutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${app.api.prefix.chat-app}")
public class UserController {

    private final UserResolutionService userResolutionService;

    public UserController(UserResolutionService userResolutionService) {
        this.userResolutionService = userResolutionService;
    }

    @PostMapping("/user/resolve")
    public ResponseEntity<UserResolveResponseDto> resolveUser(@RequestBody UserResolveRequestDto request) {
        UserResolveResponseDto response = userResolutionService.resolveUser(request.getSupabaseUid());
        return ResponseEntity.ok(response);
    }
}
