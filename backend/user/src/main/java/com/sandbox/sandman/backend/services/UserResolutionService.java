package com.sandbox.sandman.backend.services;

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
    public UserResolveResponseDto resolveUser(String supabaseUidStr) {
        UUID supabaseUid;
        try {
            supabaseUid = UUID.fromString(supabaseUidStr);
        } catch (IllegalArgumentException e) {
            log.warn("resolveUser invalid UUID input='{}'", supabaseUidStr);
            throw e;
        }

        User user = userRepository.findBySupabaseUid(supabaseUid)
                .orElseGet(() -> {
                    log.info("resolveUser creating new user supabaseUid={}", supabaseUid);
                    User newUser = new User();
                    newUser.setSupabaseUid(supabaseUid);
                    newUser.setDisplayName("User");
                    return userRepository.save(newUser);
                });

        log.debug("resolveUser resolved userId={} supabaseUid={}", user.getId(), supabaseUid);
        return new UserResolveResponseDto(user.getId());
    }
}
