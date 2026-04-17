package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.model.dto.ProfileCreateRequestDto;
import com.sandbox.sandman.backend.model.dto.ProfileDto;
import com.sandbox.sandman.backend.model.entity.Profile;
import com.sandbox.sandman.backend.repositories.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProfileService {

    private final ProfileRepository profileRepository;

    public ProfileService(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    public ProfileDto getProfile(String supabaseUidStr) {
        UUID uid = UUID.fromString(supabaseUidStr);
        Profile profile = profileRepository.findById(uid)
                .orElseThrow(() -> new RuntimeException("Profile not found"));
        return toDto(profile);
    }

    @Transactional
    public ProfileDto createOrUpdateProfile(ProfileCreateRequestDto request) {
        UUID uid = UUID.fromString(request.getSupabaseUid());

        Profile profile = profileRepository.findById(uid)
                .orElseGet(() -> {
                    Profile p = new Profile();
                    p.setSupabaseUid(uid);
                    return p;
                });

        profile.setUsername(request.getUsername());
        profile.setEmail(request.getEmail());
        Profile saved = profileRepository.save(profile);
        return toDto(saved);
    }

    private ProfileDto toDto(Profile profile) {
        return new ProfileDto(
                profile.getSupabaseUid().toString(),
                profile.getUsername(),
                profile.getEmail(),
                profile.getRole(),
                profile.getAvatarUrl()
        );
    }
}
