package com.sandbox.sandman.backend.controllers;

import com.sandbox.sandman.backend.commonauth.CurrentUser;
import com.sandbox.sandman.backend.model.dto.ProfileDto;
import com.sandbox.sandman.backend.model.dto.ProfileUpsertRequest;
import com.sandbox.sandman.backend.services.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${app.api.prefix.jobjab}/profile")
@RequiredArgsConstructor
public class ProfileController {
    private final CurrentUser currentUser;
    private final ProfileService profileService;

    @GetMapping
    public ResponseEntity<ProfileDto> get() {
        return ResponseEntity.ok(profileService.get(currentUser.requireUserId()));
    }

    @PutMapping
    public ResponseEntity<ProfileDto> upsert(@RequestBody ProfileUpsertRequest req) {
        return ResponseEntity.ok(profileService.upsert(currentUser.requireUserId(), req));
    }
}
