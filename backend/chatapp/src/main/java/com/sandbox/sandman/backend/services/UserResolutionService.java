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
    public UserResolveResponseDto resolveUser(String supabaseUidStr, String email, String username) {
        UUID supabaseUid = UUID.fromString(supabaseUidStr);

        User user = userRepository.findBySupabaseUid(supabaseUid)
                .orElseGet(() -> createUser(supabaseUid, email, username));

        return new UserResolveResponseDto(user.getId());
    }

    private User createUser(UUID supabaseUid, String email, String username) {
        User user = new User();
        user.setSupabaseUid(supabaseUid);
        user.setEmail(email);
        user.setUsername(username);
        user.setPasswordHash("supabase-auth");
        user.setRole("USER");
        return userRepository.save(user);
    }
}
