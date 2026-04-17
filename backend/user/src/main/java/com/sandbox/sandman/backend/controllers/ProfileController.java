package com.sandbox.sandman.backend.controllers;

import com.sandbox.sandman.backend.model.dto.ProfileCreateRequestDto;
import com.sandbox.sandman.backend.model.dto.ProfileDto;
import com.sandbox.sandman.backend.services.ProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${app.api.prefix.user}")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/profile/{supabaseUid}")
    public ResponseEntity<ProfileDto> getProfile(@PathVariable String supabaseUid) {
        return ResponseEntity.ok(profileService.getProfile(supabaseUid));
    }

    @PostMapping("/profile")
    public ResponseEntity<ProfileDto> createOrUpdateProfile(@RequestBody ProfileCreateRequestDto request) {
        return ResponseEntity.ok(profileService.createOrUpdateProfile(request));
    }
}
