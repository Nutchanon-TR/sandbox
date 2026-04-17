package com.sandbox.sandman.backend.controllers;

import com.sandbox.sandman.backend.model.dto.UserResolveRequestDto;
import com.sandbox.sandman.backend.model.dto.UserResolveResponseDto;
import com.sandbox.sandman.backend.services.UserResolutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${app.api.prefix.user}")
public class UserController {

    private final UserResolutionService userResolutionService;

    public UserController(UserResolutionService userResolutionService) {
        this.userResolutionService = userResolutionService;
    }

    @PostMapping("/sync")
    public ResponseEntity<UserResolveResponseDto> syncUser(@RequestBody UserResolveRequestDto request) {
        UserResolveResponseDto response = userResolutionService.resolveUser(request.getSupabaseUid());
        return ResponseEntity.ok(response);
    }
}
