package com.sandbox.sandman.backend.services;

import com.sandbox.sandman.backend.model.dto.ChatDto.UserResolveResponseDto;
import com.sandbox.sandman.backend.model.entity.ChatEntity.User;
import com.sandbox.sandman.backend.repositories.ChatRepository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserResolutionService {

    private final UserRepository userRepository;

    public UserResolutionService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserResolveResponseDto resolveUser(String supabaseUidStr) {
        UUID supabaseUid = UUID.fromString(supabaseUidStr);

        User user = userRepository.findBySupabaseUid(supabaseUid)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setSupabaseUid(supabaseUid);
                    newUser.setDisplayName("User"); // Default to prevent constraint error
                    return userRepository.save(newUser);
                });

        return new UserResolveResponseDto(user.getId());
    }
}
