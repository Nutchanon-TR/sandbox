package com.sandbox.sandman.backend.controllers;

import com.sandbox.sandman.backend.model.dto.UserDto.ProfileCreateRequestDto;
import com.sandbox.sandman.backend.model.dto.UserDto.ProfileDto;
import com.sandbox.sandman.backend.services.ProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController("userProfileController")
@RequestMapping("${app.api.prefix.chat-app}")
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
