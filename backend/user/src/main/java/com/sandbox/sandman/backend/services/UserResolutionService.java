package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.model.dto.UserResolveRequestDto;
import com.sandbox.sandman.backend.model.dto.UserResolveResponseDto;
import com.sandbox.sandman.backend.model.entity.User;
import com.sandbox.sandman.backend.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserResolutionService {

    private static final Logger log = LoggerFactory.getLogger(UserResolutionService.class);

    private final UserRepository userRepository;

    public UserResolutionService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserResolveResponseDto resolveUser(UserResolveRequestDto request) {
        UUID supabaseUid;
        try {
            supabaseUid = UUID.fromString(request.getSupabaseUid());
        } catch (IllegalArgumentException e) {
            log.warn("resolveUser invalid UUID input='{}'", request.getSupabaseUid());
            throw e;
        }

        String displayName = (request.getUsername() != null && !request.getUsername().isBlank())
                ? request.getUsername()
                : "User";

        User user = userRepository.findBySupabaseUid(supabaseUid)
                .map(existing -> {
                    boolean dirty = false;
                    if (request.getAvatarUrl() != null && !request.getAvatarUrl().equals(existing.getAvatarUrl())) {
                        existing.setAvatarUrl(request.getAvatarUrl());
                        dirty = true;
                    }
                    if (request.getUsername() != null && !request.getUsername().isBlank()
                            && !request.getUsername().equals(existing.getDisplayName())) {
                        existing.setDisplayName(request.getUsername());
                        dirty = true;
                    }
                    return dirty ? userRepository.save(existing) : existing;
                })
                .orElseGet(() -> {
                    log.info("resolveUser creating new user supabaseUid={}", supabaseUid);
                    User newUser = new User();
                    newUser.setSupabaseUid(supabaseUid);
                    newUser.setDisplayName(displayName);
                    newUser.setAvatarUrl(request.getAvatarUrl());
                    return userRepository.save(newUser);
                });

        log.debug("resolveUser resolved userId={} supabaseUid={}", user.getId(), supabaseUid);
        return new UserResolveResponseDto(user.getId());
    }
}
